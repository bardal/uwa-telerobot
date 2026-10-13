package au.edu.uwa.mech.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every gateway setting. Parsed from the command line and the environment; a flag overrides its
 * environment variable.
 */
public record GatewayConfig(
    int port,
    String momUrlHost,
    int momPort,
    int labAdminPort,
    List<String> allowedOrigins,
    String originSecret,
    boolean dev,
    String staticDir,
    String archiveDir,
    String cognitoIssuer,
    List<String> cognitoClientIds,
    String turnstileSecret,
    Map<String, String> pins,
    String archiveBucket,
    boolean instanceStop,
    String commit,
    String awsRegion,
    String originSecretParam,
    String turnstileSecretParam) {

    public static final String DEFAULT_COMMIT = "dev";

    public GatewayConfig {
        allowedOrigins = List.copyOf(allowedOrigins);
        cognitoClientIds = List.copyOf(cognitoClientIds);
        pins = Map.copyOf(pins);
    }

    public static GatewayConfig parse(String[] args, Map<String, String> env) {
        int port = 8080;
        String momHost = "127.0.0.1";
        int momPort = 4444;
        int labAdminPort = 30564;
        List<String> origins = new ArrayList<>();
        String originSecret = blankToNull(env.get("ORIGIN_SECRET"));
        boolean dev = false;
        String staticDir = null;
        String archiveDir = null;
        String issuer = blankToNull(env.get("COGNITO_ISSUER"));
        List<String> clientIds = new ArrayList<>();
        String turnstile = blankToNull(env.get("TURNSTILE_SECRET"));
        boolean instanceStop = "true".equalsIgnoreCase(env.get("INSTANCE_STOP"));
        String commit = null;

        boolean originsFromFlags = false;
        boolean clientIdsFromFlags = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--dev" -> dev = true;
                case "--instance-stop" -> instanceStop = true;
                case "--port" -> port = parseInt(a, value(args, ++i));
                case "--mom-host" -> momHost = value(args, ++i);
                case "--mom-port" -> momPort = parseInt(a, value(args, ++i));
                case "--lab-admin-port" -> labAdminPort = parseInt(a, value(args, ++i));
                case "--allowed-origin" -> {
                    if (!originsFromFlags) origins.clear();
                    originsFromFlags = true;
                    origins.add(value(args, ++i));
                }
                case "--static-dir" -> staticDir = value(args, ++i);
                case "--archive-dir" -> archiveDir = value(args, ++i);
                case "--cognito-issuer" -> issuer = value(args, ++i);
                case "--cognito-client-id" -> {
                    if (!clientIdsFromFlags) clientIds.clear();
                    clientIdsFromFlags = true;
                    clientIds.add(value(args, ++i));
                }
                case "--origin-secret" -> originSecret = blankToNull(value(args, ++i));
                case "--turnstile-secret" -> turnstile = blankToNull(value(args, ++i));
                case "--commit" -> commit = value(args, ++i);
                default -> throw new IllegalArgumentException("unknown option " + a);
            }
        }
        if (!originsFromFlags && origins.isEmpty()) origins.addAll(split(env.get("ALLOWED_ORIGIN")));
        if (!clientIdsFromFlags && clientIds.isEmpty()) clientIds.addAll(split(env.get("COGNITO_CLIENT_IDS")));

        if (commit == null) commit = manifestCommit();
        return new GatewayConfig(port, momHost, momPort, labAdminPort, origins, originSecret, dev, staticDir,
            archiveDir, issuer, clientIds, turnstile, Map.of(), blankToNull(env.get("ARCHIVE_BUCKET")), instanceStop,
            commit, blankToNull(env.get("AWS_REGION")), blankToNull(env.get("ORIGIN_SECRET_PARAM")),
            blankToNull(env.get("TURNSTILE_SECRET_PARAM")));
    }

    /** The shade plugin stamps Implementation-Version with the build's git commit; "dev" when run from classes. */
    static String manifestCommit() {
        String v = GatewayConfig.class.getPackage().getImplementationVersion();
        return v == null || v.isBlank() ? DEFAULT_COMMIT : v;
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) throw new IllegalArgumentException("missing value for " + args[i - 1]);
        return args[i];
    }

    private static int parseInt(String flag, String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number for " + flag + ": " + v);
        }
    }


    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static List<String> split(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }
}
