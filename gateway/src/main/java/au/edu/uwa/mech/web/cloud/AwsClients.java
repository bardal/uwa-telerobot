package au.edu.uwa.mech.web.cloud;

import java.time.Duration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

/** Settings shared by the gateway's AWS clients (SSM, S3, EC2). */
public final class AwsClients {
    /** A whole call, retries included, gives up after this, so a stuck AWS call cannot hang a timer. */
    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);
    /** Each attempt within a call. */
    public static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(8);

    private AwsClients() {}

    public static ClientOverrideConfiguration overrides() {
        return ClientOverrideConfiguration.builder()
            .apiCallTimeout(CALL_TIMEOUT)
            .apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
            .build();
    }
}
