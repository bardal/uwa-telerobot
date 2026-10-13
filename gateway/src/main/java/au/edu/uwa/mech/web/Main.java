package au.edu.uwa.mech.web;

/** Starts the gateway: {@code java -jar gateway.jar --port 8080 --allowed-origin https://... }. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        GatewayConfig config;
        try {
            config = GatewayConfig.parse(args, System.getenv());
        } catch (IllegalArgumentException e) {
            System.err.println("gateway: " + e.getMessage());
            System.exit(2);
            return;
        }
        GatewayServer server = new GatewayServer(config, new StubSessionHandler(config), System::currentTimeMillis);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {
                // shutting down anyway
            }
        }));
        server.start();
        System.out.println("gateway " + config.commit() + " listening on port " + server.port()
            + (config.dev() ? " (dev)" : ""));
    }
}
