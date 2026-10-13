package au.edu.uwa.mech.web.cloud;

import au.edu.uwa.mech.web.Bans;
import au.edu.uwa.mech.web.GatewayConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The AWS start-up configuration. The systemd unit's environment carries the plain settings
 * ({@code ALLOWED_ORIGIN}, {@code COGNITO_ISSUER}, {@code COGNITO_CLIENT_IDS}, {@code ARCHIVE_BUCKET},
 * {@code AWS_REGION}) and the names of the two secret parameters ({@code ORIGIN_SECRET_PARAM},
 * {@code TURNSTILE_SECRET_PARAM}); the secrets, PINs and ban list are read from SSM. Anything
 * missing stops the gateway at start with a message naming it.
 */
public final class CloudConfig {
    public static final String PIN_REGISTERED = "/telerobot/pins/registered";
    public static final String PIN_ADMIN = "/telerobot/pins/admin";
    public static final String ARCHIVE_BYTES = "/telerobot/archive-bytes";
    public static final String DEFAULT_REGION = "eu-west-2";

    private CloudConfig() {}

    /** On AWS: not dev, and either secret-parameter variable is set. */
    public static boolean onAws(GatewayConfig c) {
        return !c.dev() && (c.originSecretParam() != null || c.turnstileSecretParam() != null);
    }

    public static String region(GatewayConfig c) {
        return c.awsRegion() == null ? DEFAULT_REGION : c.awsRegion();
    }

    /**
     * Checks the environment settings and reads every required parameter: a copy of {@code c} with
     * the origin secret, Turnstile secret and PINs from SSM.
     *
     * @throws IllegalStateException naming each missing variable or parameter
     */
    public static GatewayConfig load(GatewayConfig c, ParamStore ssm) {
        List<String> missing = new ArrayList<>();
        if (c.allowedOrigins().isEmpty()) missing.add("environment variable ALLOWED_ORIGIN");
        if (c.cognitoIssuer() == null) missing.add("environment variable COGNITO_ISSUER");
        if (c.cognitoClientIds().isEmpty()) missing.add("environment variable COGNITO_CLIENT_IDS");
        if (c.archiveBucket() == null) missing.add("environment variable ARCHIVE_BUCKET");
        if (c.originSecretParam() == null) missing.add("environment variable ORIGIN_SECRET_PARAM");
        if (c.turnstileSecretParam() == null) missing.add("environment variable TURNSTILE_SECRET_PARAM");

        String origin = c.originSecretParam() == null ? null : read(ssm, c.originSecretParam(), missing);
        String turnstile = c.turnstileSecretParam() == null ? null : read(ssm, c.turnstileSecretParam(), missing);
        String registered = read(ssm, PIN_REGISTERED, missing);
        String admin = read(ssm, PIN_ADMIN, missing);
        read(ssm, Bans.PARAM, missing); // pre-created as [] by the deploy script; Bans reads it
        String bytes = read(ssm, ARCHIVE_BYTES, missing); // pre-created as 0; the byte counter's seed

        if (!missing.isEmpty()) {
            throw new IllegalStateException("cannot start on AWS, missing or blank: " + String.join(", ", missing));
        }
        if (bytes != null) parseBytes(bytes); // a corrupt counter would silently lift the archive cap
        return c.withSecrets(origin, turnstile,
            Map.of(GatewayConfig.PIN_REGISTERED, registered, GatewayConfig.PIN_ADMIN, admin));
    }

    /**
     * The archive byte counter's stored value; 0 if absent.
     *
     * @throws IllegalStateException if it is not a whole number of bytes
     */
    public static long archiveBytes(ParamStore ssm) {
        Optional<String> v = ssm.get(ARCHIVE_BYTES);
        return v.isEmpty() ? 0 : parseBytes(v.get());
    }

    private static long parseBytes(String v) {
        try {
            long n = Long.parseLong(v.trim());
            if (n >= 0) return n;
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalStateException("SSM parameter " + ARCHIVE_BYTES + " must be a whole number of bytes, not \"" + v + "\"");
    }

    private static String read(ParamStore ssm, String name, List<String> missing) {
        Optional<String> v;
        try {
            v = ssm.get(name);
        } catch (RuntimeException e) {
            throw new IllegalStateException("cannot read SSM parameter " + name + ": " + e.getMessage(), e);
        }
        if (v.isEmpty() || v.get().isBlank()) {
            missing.add("SSM parameter " + name);
            return null;
        }
        return v.get();
    }
}
