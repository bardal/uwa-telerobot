package au.edu.uwa.mech.web.cloud;

import java.util.Optional;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

/**
 * SSM Parameter Store. Reads decrypt (SecureString secrets and PINs); writes overwrite an existing
 * String parameter without naming a type, which is all the instance role allows (on
 * {@code /telerobot/heartbeat}, {@code /telerobot/bans} and {@code /telerobot/archive-bytes}).
 */
public final class SsmParamStore implements ParamStore {
    private final SsmClient ssm;

    public SsmParamStore(SsmClient ssm) {
        this.ssm = ssm;
    }

    /** Instance-role credentials, the URL-connection HTTP client. */
    public static SsmParamStore create(String region) {
        return new SsmParamStore(SsmClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .overrideConfiguration(AwsClients.overrides())
            .build());
    }

    @Override
    public Optional<String> get(String name) {
        try {
            return Optional.ofNullable(ssm.getParameter(GetParameterRequest.builder().name(name).withDecryption(true)
                .build()).parameter().value());
        } catch (ParameterNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public void put(String name, String value) {
        ssm.putParameter(PutParameterRequest.builder().name(name).value(value).overwrite(true).build());
    }
}
