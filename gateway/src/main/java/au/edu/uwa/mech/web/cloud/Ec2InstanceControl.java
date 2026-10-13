package au.edu.uwa.mech.web.cloud;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;

/**
 * Stops this EC2 instance: an IMDSv2 token (PUT with a TTL header), then the instance id and the
 * region from instance metadata, then {@code StopInstances}. The instance role allows that only on
 * its own instance, tagged {@code telerobot=lab}. Nothing is contacted until {@link #stopSelf()}.
 */
public final class Ec2InstanceControl implements InstanceControl {
    private static final Logger LOG = LoggerFactory.getLogger(Ec2InstanceControl.class);
    public static final String IMDS = "http://169.254.169.254";
    static final String TOKEN_TTL_HEADER = "X-aws-ec2-metadata-token-ttl-seconds";
    static final String TOKEN_HEADER = "X-aws-ec2-metadata-token";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String imds;
    private final Function<String, Ec2Client> clients;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public Ec2InstanceControl() {
        this(IMDS, region -> Ec2Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build());
    }

    /** {@code clients} makes an EC2 client for a region. */
    Ec2InstanceControl(String imdsBase, Function<String, Ec2Client> clients) {
        this.imds = imdsBase;
        this.clients = clients;
    }

    @Override
    public void stopSelf() {
        String token = send(HttpRequest.newBuilder(URI.create(imds + "/latest/api/token"))
            .header(TOKEN_TTL_HEADER, "60").PUT(HttpRequest.BodyPublishers.noBody()));
        String id = metadata(token, "instance-id");
        String region = metadata(token, "placement/region");
        LOG.info("stopping instance {} in {}: idle", id, region);
        try (Ec2Client ec2 = clients.apply(region)) {
            ec2.stopInstances(StopInstancesRequest.builder().instanceIds(id).build());
        }
    }

    private String metadata(String token, String path) {
        return send(HttpRequest.newBuilder(URI.create(imds + "/latest/meta-data/" + path)).header(TOKEN_HEADER, token).GET());
    }

    private String send(HttpRequest.Builder b) {
        HttpRequest req = b.timeout(TIMEOUT).build();
        try {
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("instance metadata " + req.uri().getPath() + " answered " + r.statusCode());
            }
            String body = r.body().trim();
            if (body.isEmpty()) throw new IllegalStateException("instance metadata " + req.uri().getPath() + " was empty");
            return body;
        } catch (IOException e) {
            throw new IllegalStateException("instance metadata unreachable: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
