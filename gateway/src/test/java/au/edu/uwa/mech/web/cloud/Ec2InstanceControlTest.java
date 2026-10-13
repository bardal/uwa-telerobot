package au.edu.uwa.mech.web.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesResponse;

/** IMDSv2 against a local fake metadata server, and a fake EC2 client: no AWS calls. */
class Ec2InstanceControlTest {
    HttpServer imds;
    final List<String> requests = new ArrayList<>();
    final List<String> regions = new ArrayList<>();
    final List<StopInstancesRequest> stops = new ArrayList<>();
    volatile String token = "tok-1";

    @BeforeEach
    void startImds() throws Exception {
        imds = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        imds.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            synchronized (requests) {
                requests.add(method + " " + path);
            }
            int status;
            String body;
            if (method.equals("PUT") && path.equals("/latest/api/token")) {
                String ttl = ex.getRequestHeaders().getFirst("X-aws-ec2-metadata-token-ttl-seconds");
                status = ttl != null && ttl.matches("\\d+") ? 200 : 400;
                body = status == 200 ? token : "";
            } else if (!method.equals("GET") || !token.equals(ex.getRequestHeaders().getFirst("X-aws-ec2-metadata-token"))) {
                status = 401;
                body = "";
            } else if (path.equals("/latest/meta-data/instance-id")) {
                status = 200;
                body = "i-0abc123";
            } else if (path.equals("/latest/meta-data/placement/region")) {
                status = 200;
                body = "eu-west-2";
            } else {
                status = 404;
                body = "";
            }
            byte[] b = body.getBytes(StandardCharsets.US_ASCII);
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) ex.getResponseBody().write(b);
            ex.close();
        });
        imds.start();
    }

    @AfterEach
    void stopImds() {
        imds.stop(0);
    }

    Ec2InstanceControl control() {
        String base = "http://127.0.0.1:" + imds.getAddress().getPort();
        return new Ec2InstanceControl(base, region -> {
            regions.add(region);
            return new Ec2Client() {
                @Override public String serviceName() { return "ec2"; }
                @Override public void close() { }
                @Override
                public StopInstancesResponse stopInstances(StopInstancesRequest r) {
                    stops.add(r);
                    return StopInstancesResponse.builder().build();
                }
            };
        });
    }

    @Test
    void tokenThenInstanceIdAndRegionThenStopInstances() {
        control().stopSelf();
        assertEquals(List.of("PUT /latest/api/token", "GET /latest/meta-data/instance-id",
            "GET /latest/meta-data/placement/region"), requests);
        assertEquals(List.of("eu-west-2"), regions);
        assertEquals(1, stops.size());
        assertEquals(List.of("i-0abc123"), stops.get(0).instanceIds());
    }

    @Test
    void aMetadataFailureIsAnErrorAndNothingIsStopped() {
        imds.removeContext("/");
        imds.createContext("/", ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        RuntimeException e = assertThrows(RuntimeException.class, () -> control().stopSelf());
        assertTrue(e.getMessage().contains("500"), e.getMessage());
        assertEquals(0, stops.size());
    }
}
