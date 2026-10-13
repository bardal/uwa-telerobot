package au.edu.uwa.mech.web;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.ServerWebSocketContainer;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gateway's only network endpoint: {@code /healthz}, the {@code /ws} WebSocket and, in dev mode,
 * the static UI, {@code /api/status} and the public archive.
 */
public final class GatewayServer {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayServer.class);

    public static final int MAX_TEXT_BYTES = Protocol.MAX_MESSAGE_BYTES;
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(11);

    /** Timers: slot lease before the socket opens, time allowed to say hello, how often leases are swept. */
    public record Timing(long leaseMillis, long helloDeadlineMillis, long sweepMillis) {
        public static final Timing DEFAULT = new Timing(Admission.DEFAULT_LEASE_MILLIS, 15_000, 2_000);
    }

    private final GatewayConfig config;
    private final SessionHandler handler;
    private final Admission admission;
    private final Server server = new Server();
    private final ServerConnector connector = new ServerConnector(server);
    private final Timing timing;
    private final java.util.concurrent.ScheduledExecutorService timers =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gateway-timers");
            t.setDaemon(true);
            return t;
        });
    private volatile Supplier<String> labState = () -> "ready";

    public GatewayServer(GatewayConfig config, SessionHandler handler, LongSupplier clockMillis) {
        this(config, handler, clockMillis, Timing.DEFAULT);
    }

    public GatewayServer(GatewayConfig config, SessionHandler handler, LongSupplier clockMillis, Timing timing) {
        this.config = config;
        this.handler = handler;
        this.timing = timing;
        this.admission = new Admission(config, clockMillis, timing.leaseMillis());
        connector.setPort(config.port());
        connector.setIdleTimeout(IDLE_TIMEOUT.toMillis());
        server.addConnector(connector);

        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.addHandler(context(new HealthHandler(), "/healthz"));

        ContextHandler ws = context(null, "/ws");
        ws.setHandler(WebSocketUpgradeHandler.from(server, ws, container -> {
            container.setMaxTextMessageSize(MAX_TEXT_BYTES);
            // Binary is never valid from a client. Let frames up to the text limit reach the listener,
            // which closes with 1003 (policy: unsupported data); bigger ones die as 1009 anyway.
            container.setMaxBinaryMessageSize(MAX_TEXT_BYTES);
            container.setMaxFrameSize(MAX_TEXT_BYTES);
            container.setIdleTimeout(IDLE_TIMEOUT);
            container.addMapping("/*", this::createWebSocket);
        }));
        contexts.addHandler(ws);

        if (config.dev()) {
            contexts.addHandler(context(new StatusHandler(), "/api/status"));
            if (config.archiveDir() != null) {
                contexts.addHandler(staticContext("/archive/public", config.archiveDir()));
            }
            if (config.staticDir() != null) {
                contexts.addHandler(staticContext("/", config.staticDir()));
            }
        }
        server.setHandler(contexts);
    }

    private static ContextHandler context(Handler handler, String path) {
        ContextHandler c = new ContextHandler(handler, path);
        c.setAllowNullPathInContext(true); // "/healthz" and "/ws" without a trailing slash must not redirect
        return c;
    }

    private ContextHandler staticContext(String contextPath, String dir) {
        ResourceHandler resources = new ResourceHandler();
        resources.setBaseResource(ResourceFactory.of(server).newResource(Path.of(dir)));
        resources.setDirAllowed(false);
        resources.setWelcomeFiles(java.util.List.of("index.html"));
        return context(resources, contextPath);
    }

    public void start() throws Exception {
        if (config.dev()) {
            LOG.warn("DEV MODE IS ON: {}never run this on a public host", config.originSecret() == null
                ? "the origin secret header is NOT checked (only Origin is); " : "");
        }
        server.start();
        timers.scheduleWithFixedDelay(() -> {
            try {
                admission.sweep();
            } catch (RuntimeException e) { // an escaping exception would silently end the schedule
                LOG.warn("admission sweep failed", e);
            }
        }, timing.sweepMillis(), timing.sweepMillis(),
            java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void stop() throws Exception {
        timers.shutdownNow();
        server.stop();
    }

    /** The bound port (useful when configured with 0). */
    public int port() {
        return connector.getLocalPort();
    }

    /** Open, admitted WebSocket connections. */
    public int sessions() {
        return admission.viewers();
    }

    /** Lets the lab bridge (Task 5) report its state in /healthz. */
    public void setLabState(Supplier<String> labState) {
        this.labState = labState;
    }

    Admission admission() {
        return admission;
    }

    // ---- WebSocket upgrade -------------------------------------------------------------------

    private Object createWebSocket(org.eclipse.jetty.websocket.server.ServerUpgradeRequest request,
                                   org.eclipse.jetty.websocket.server.ServerUpgradeResponse response,
                                   Callback callback) {
        java.util.function.Function<String, String> header = name -> request.getHeaders().get(name);
        String socketIp = socketIp(request);
        Admission.Admitted admitted = admission.admit(header, socketIp);
        if (admitted.refusal() != null) {
            String why = admitted.refusal();
            int status = (why.equals(Admission.BAD_SECRET) || why.equals(Admission.BAD_ORIGIN)) ? 403 : 429;
            LOG.info("refused handshake from {}: {}", socketIp, why);
            // the body never says which check failed; the reason is in the log only
            Response.writeError(request, response, callback, status, status == 403 ? "forbidden" : "too many");
            return null;
        }
        return new Connection(admitted.reservation());
    }

    private static String socketIp(Request request) {
        java.net.SocketAddress a = request.getConnectionMetaData().getRemoteSocketAddress();
        if (a instanceof java.net.InetSocketAddress isa && isa.getAddress() != null) {
            return isa.getAddress().getHostAddress();
        }
        return String.valueOf(a);
    }

    /** One client socket: strict parse, hello gate, then hand over to the {@link SessionHandler}. */
    public final class Connection implements Session.Listener.AutoDemanding, ClientConnection {
        private final Admission.Reservation reservation;
        private final String ip;
        private volatile Session session;
        private volatile Object attachment;
        private volatile boolean helloSeen;
        private volatile java.util.concurrent.ScheduledFuture<?> helloTimer;

        Connection(Admission.Reservation reservation) {
            this.reservation = reservation;
            this.ip = reservation.ip();
        }

        @Override
        public void onWebSocketOpen(Session session) {
            this.session = session;
            if (!reservation.confirm()) { // the lease ran out before we got here
                session.close(1013, "try again", org.eclipse.jetty.websocket.api.Callback.NOOP);
                return;
            }
            helloTimer = timers.schedule(() -> {
                if (!helloSeen) {
                    send(new Out.Error("unauthorised", "say hello first"));
                    close(1008, "unauthorised");
                }
            }, timing.helloDeadlineMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        @Override
        public void onWebSocketText(String message) {
            try {
                if (!helloSeen) {
                    In in;
                    try {
                        in = Protocol.parse(message);
                    } catch (BadMessage e) {
                        in = null;
                    }
                    if (!(in instanceof In.Hello hello)) {
                        send(new Out.Error("unauthorised", "say hello first"));
                        close(1008, "unauthorised");
                        return;
                    }
                    helloSeen = true;
                    var timer = helloTimer;
                    if (timer != null) timer.cancel(false);
                    handler.onHello(this, hello);
                    return;
                }
                In in;
                try {
                    in = Protocol.parse(message);
                } catch (BadMessage e) {
                    handler.onBadMessage(this, e);
                    return;
                }
                if (in instanceof In.Hello) {
                    handler.onBadMessage(this, new BadMessage("second hello"));
                    return;
                }
                handler.onMessage(this, in);
            } catch (RuntimeException e) {
                LOG.warn("handler failed", e);
                close(1011, "internal error");
            }
        }

        @Override
        public void onWebSocketBinary(ByteBuffer payload, org.eclipse.jetty.websocket.api.Callback callback) {
            callback.succeed();
            close(1003, "binary not accepted");
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            LOG.debug("websocket error from {}", ip, cause);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            finish();
        }

        private final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();

        private void finish() {
            if (!finished.compareAndSet(false, true)) return;
            var timer = helloTimer;
            if (timer != null) timer.cancel(false);
            reservation.release();
            if (helloSeen) {
                try {
                    handler.onClose(this);
                } catch (RuntimeException e) {
                    LOG.warn("handler onClose failed", e);
                }
            }
        }

        // ClientConnection
        @Override public String remoteIp() { return ip; }

        @Override
        public void send(Out message) {
            Session s = session;
            if (s != null && s.isOpen()) s.sendText(Out.json(message), org.eclipse.jetty.websocket.api.Callback.NOOP);
        }

        @Override
        public void sendBinary(byte[] data) {
            Session s = session;
            if (s != null && s.isOpen()) s.sendBinary(ByteBuffer.wrap(data), org.eclipse.jetty.websocket.api.Callback.NOOP);
        }

        @Override
        public void close(int code, String reason) {
            Session s = session;
            if (s != null && s.isOpen()) s.close(code, reason, org.eclipse.jetty.websocket.api.Callback.NOOP);
        }

        @Override public boolean isOpen() { Session s = session; return s != null && s.isOpen(); }
        @Override public Object attachment() { return attachment; }
        @Override public void attach(Object state) { this.attachment = state; }
    }

    // ---- HTTP --------------------------------------------------------------------------------

    private final class HealthHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
                Response.writeError(request, response, callback, 405);
                return true;
            }
            String body = "{\"ok\":true,\"sessions\":" + sessions() + ",\"commit\":" + quote(config.commit())
                + ",\"lab\":" + quote(labState.get()) + "}";
            writeJson(response, callback, body);
            return true;
        }
    }

    private static final class StatusHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            writeJson(response, callback, "{\"state\":\"running\",\"healthy\":true}");
            return true;
        }
    }

    private static void writeJson(Response response, Callback callback, String body) {
        response.setStatus(200);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json");
        response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-store");
        response.write(true, ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)), callback);
    }

    private static String quote(String s) {
        return Out.json(s);
    }
}
