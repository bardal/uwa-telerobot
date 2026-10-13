package au.edu.uwa.mech.web;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * The gateway's only network endpoint: {@code /healthz}, the {@code /ws} WebSocket and, in dev mode
 * only, the static UI, stand-ins for the Lambda's {@code /api/status} and {@code /api/wake}, the
 * public archive and {@code POST /dev/flush}. Without {@code --dev} each of those answers 404.
 */
public final class GatewayServer {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayServer.class);

    public static final int MAX_TEXT_BYTES = Protocol.MAX_MESSAGE_BYTES;
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(11);
    /** How long a client has to complete a close we started before its TCP connection is dropped. */
    public static final long CLOSE_GRACE_MILLIS = 2_000;

    /**
     * How often each open socket is pinged, so CloudFront and other intermediaries never see it idle
     * and drop it while the user is only watching.
     */
    public static final long PING_MILLIS = 30_000;

    /**
     * Timers: slot lease before the socket opens, time allowed to say hello, how often leases are
     * swept, and how often each open socket is pinged.
     */
    public record Timing(long leaseMillis, long helloDeadlineMillis, long sweepMillis, long pingMillis) {
        public static final Timing DEFAULT = new Timing(Admission.DEFAULT_LEASE_MILLIS, 15_000, 2_000);

        public Timing(long leaseMillis, long helloDeadlineMillis, long sweepMillis) {
            this(leaseMillis, helloDeadlineMillis, sweepMillis, PING_MILLIS);
        }
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
    private volatile Runnable devFlush = () -> { };
    /** Where the dev fakeStatus sequence is: 0 stopped, 1 starting, 2 running. */
    private final java.util.concurrent.atomic.AtomicInteger fakeStep = new java.util.concurrent.atomic.AtomicInteger();

    public GatewayServer(GatewayConfig config, SessionHandler handler, LongSupplier clockMillis) {
        this(config, handler, clockMillis, Timing.DEFAULT);
    }

    public GatewayServer(GatewayConfig config, SessionHandler handler, LongSupplier clockMillis, Timing timing) {
        this.config = config;
        this.handler = handler;
        this.timing = timing;
        this.admission = new Admission(config, clockMillis, timing.leaseMillis());
        connector.setPort(config.port());
        connector.setHost(config.bind()); // null: every interface (production: CloudFront reaches it over the EIP)
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
            // dev-only routes: without --dev none of these exist, so each answers 404
            contexts.addHandler(context(new StatusHandler(), "/api/status"));
            contexts.addHandler(context(new WakeHandler(), "/api/wake"));
            contexts.addHandler(context(new FlushHandler(), "/dev/flush"));
            if (config.archiveDir() != null) {
                // only the public half of the archive; private/ (the audit log) is never served
                Path pub;
                try {
                    pub = realDir(Files.createDirectories(Path.of(config.archiveDir(), "public")));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                contexts.addHandler(staticContext("/archive/public", pub, null));
            }
            if (config.staticDir() != null) {
                Path ui = Path.of(config.staticDir());
                if (Files.isDirectory(ui)) {
                    ui = realDir(ui);
                    contexts.addHandler(staticContext("/", ui, DevStatic.csp(ui)));
                } else {
                    LOG.warn("static directory {} does not exist; the UI is not served", ui.toAbsolutePath());
                }
            }
        }
        server.setHandler(contexts);
    }

    /**
     * The real path of a directory. Jetty serves nothing from a base that is an alias (a relative
     * path, a junction, a differently cased or 8.3 Windows path): it only warns and answers 404.
     */
    private static Path realDir(Path dir) {
        try {
            return dir.toRealPath();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static ContextHandler context(Handler handler, String path) {
        ContextHandler c = new ContextHandler(handler, path);
        c.setAllowNullPathInContext(true); // "/healthz" and "/ws" without a trailing slash must not redirect
        return c;
    }

    private ContextHandler staticContext(String contextPath, Path dir, String csp) {
        ResourceHandler resources = new ResourceHandler() {
            // Jetty's default factory caches content and revalidates it at most once a second, so a
            // day index rewritten by a flush could be served stale. Dev serving is small: read it fresh.
            @Override
            protected org.eclipse.jetty.http.content.HttpContent.Factory newHttpContentFactory() {
                return new org.eclipse.jetty.http.content.ResourceHttpContentFactory(getBaseResource(), getMimeTypes());
            }
        };
        resources.setBaseResource(ResourceFactory.of(server).newResource(dir));
        resources.setDirAllowed(false);
        resources.setWelcomeFiles(java.util.List.of("index.html"));
        ContextHandler c = context(new DevStatic(resources, csp), contextPath);
        c.clearAliasChecks(); // no alias is served: no symlink is followed out of the directory
        return c;
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

    /** The address listened on, or null for every interface. */
    public String host() {
        return connector.getHost();
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

    /** What dev mode's {@code POST /dev/flush} runs (Main: the archive flush). */
    public void setDevFlush(Runnable flush) {
        this.devFlush = flush;
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
        private volatile java.util.concurrent.ScheduledFuture<?> pingTimer;
        private volatile boolean closing;
        private volatile java.util.concurrent.ScheduledFuture<?> closeTimer;

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
            // keepalive: an empty ping now and then; the pongs are ignored (Listener's default)
            try {
                pingTimer = timers.scheduleAtFixedRate(this::ping, timing.pingMillis(), timing.pingMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException stopping) {
                // the server is stopping
            }
        }

        private void ping() {
            Session s = session;
            if (s == null || closing || !s.isOpen()) return;
            try {
                s.sendPing(ByteBuffer.allocate(0), org.eclipse.jetty.websocket.api.Callback.NOOP);
            } catch (RuntimeException e) {
                LOG.debug("ping to {} failed", ip, e);
            }
        }

        @Override
        public void onWebSocketText(String message) {
            if (closing) return; // we have closed: nothing more from this client is acted on
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
            if (closing) return;
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
            var pings = pingTimer;
            if (pings != null) pings.cancel(false);
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

        private final java.util.concurrent.atomic.AtomicLong queuedBytes = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicInteger queuedMessages = new java.util.concurrent.atomic.AtomicInteger();

        /** Counts a frame as queued until Jetty has written it (or failed); then runs {@code written}. */
        private org.eclipse.jetty.websocket.api.Callback tracked(long bytes, Runnable written) {
            queuedBytes.addAndGet(bytes);
            queuedMessages.incrementAndGet();
            java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
            Runnable end = () -> {
                if (!done.compareAndSet(false, true)) return;
                queuedBytes.addAndGet(-bytes);
                queuedMessages.decrementAndGet();
                written.run();
            };
            return org.eclipse.jetty.websocket.api.Callback.from(end, failure -> end.run());
        }

        @Override
        public void send(Out message, Runnable written) {
            Session s = session;
            if (s == null || !s.isOpen()) {
                written.run();
                return;
            }
            String json = Out.json(message);
            s.sendText(json, tracked(json.length(), written));
        }

        @Override
        public void sendImage(Out.Image header, byte[] jpeg, Runnable written) {
            Session s = session;
            if (s == null || !s.isOpen()) {
                written.run();
                return;
            }
            String json = Out.json(header);
            s.sendText(json, tracked(json.length(), () -> {}));
            s.sendBinary(ByteBuffer.wrap(jpeg), tracked(jpeg.length, written));
        }

        @Override public long queuedBytes() { return queuedBytes.get(); }
        @Override public int queuedMessages() { return queuedMessages.get(); }

        /**
         * Starts the close handshake, ignores every later frame from the client, and drops the TCP
         * connection if the client has not completed the close within {@link #CLOSE_GRACE_MILLIS}.
         */
        @Override
        public void close(int code, String reason) {
            closing = true;
            Session s = session;
            if (s == null) return;
            if (s.isOpen()) s.close(code, reason, org.eclipse.jetty.websocket.api.Callback.NOOP);
            if (closeTimer == null) {
                try {
                    closeTimer = timers.schedule(() -> {
                        if (s.isOpen()) {
                            LOG.info("client {} ignored the close; disconnecting", ip);
                            s.disconnect();
                        }
                        finish();
                    }, CLOSE_GRACE_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.RejectedExecutionException stopping) {
                    s.disconnect(); // the server is stopping
                }
            }
        }

        @Override public boolean isOpen() { Session s = session; return s != null && s.isOpen() && !closing; }
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

    /**
     * Dev stand-in for the Lambda's {@code GET /api/status}: always running, except that
     * {@code ?fakeStatus=stopped} walks stopped, starting, running (then round again) per process, so
     * the waking page can be exercised.
     */
    private final class StatusHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
                Response.writeError(request, response, callback, 405);
                return true;
            }
            String query = request.getHttpURI().getQuery();
            boolean fake = query != null && java.util.Arrays.asList(query.split("&")).contains("fakeStatus=stopped");
            int step = fake ? fakeStep.getAndUpdate(n -> (n + 1) % 3) : 2;
            String body = switch (step) {
                case 0 -> "{\"state\":\"stopped\",\"healthy\":false,\"commit\":null}";
                case 1 -> "{\"state\":\"starting\",\"healthy\":false,\"commit\":null}";
                default -> "{\"state\":\"running\",\"healthy\":true,\"commit\":" + quote(config.commit()) + "}";
            };
            writeJson(response, callback, body);
            return true;
        }
    }

    /** Dev stand-in for the Lambda's {@code POST /api/wake}: accepted (the lab is already here). */
    private static final class WakeHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"POST".equals(request.getMethod())) {
                Response.writeError(request, response, callback, 405);
                return true;
            }
            writeJson(response, callback, 202, "{\"state\":\"starting\"}");
            return true;
        }
    }

    /** Dev only: {@code POST /dev/flush} writes the archive's day indexes now; 204. */
    private final class FlushHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"POST".equals(request.getMethod())) {
                Response.writeError(request, response, callback, 405);
                return true;
            }
            devFlush.run();
            response.setStatus(204);
            response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-store");
            response.write(true, null, callback);
            return true;
        }
    }

    /**
     * Dev static files: {@code X-Content-Type-Options: nosniff} on everything, the CSP from
     * {@code csp.json} when there is one (the policy CloudFront sets in production), and
     * {@code Cache-Control: no-cache} on {@code index.html} so a rebuilt UI is picked up.
     */
    static final class DevStatic extends Handler.Wrapper {
        private final String csp;

        DevStatic(Handler resources, String csp) {
            super(resources);
            this.csp = csp;
        }

        /** The policy in {@code dir/csp.json} ({@code {"csp":"..."}}), or null when there is no such file. */
        static String csp(Path dir) {
            Path f = dir.resolve("csp.json");
            if (!Files.isRegularFile(f)) return null;
            try {
                com.fasterxml.jackson.databind.JsonNode j = Protocol.mapper().readTree(Files.readString(f));
                com.fasterxml.jackson.databind.JsonNode p = j == null ? null : j.get("csp");
                if (p == null || !p.isTextual() || p.asText().isBlank() || p.asText().matches("(?s).*[\\r\\n].*")) {
                    throw new IllegalArgumentException(f + " must be {\"csp\":\"<one-line policy>\"}");
                }
                return p.asText();
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("cannot read " + f + ": " + e.getMessage(), e);
            }
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            response.getHeaders().put("X-Content-Type-Options", "nosniff");
            if (csp != null) response.getHeaders().put("Content-Security-Policy", csp);
            String path = Request.getPathInContext(request);
            if (path.isEmpty() || path.endsWith("/") || path.endsWith("/index.html")) {
                response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-cache");
            }
            return super.handle(request, response, callback);
        }
    }

    private static void writeJson(Response response, Callback callback, String body) {
        writeJson(response, callback, 200, body);
    }

    private static void writeJson(Response response, Callback callback, int status, String body) {
        response.setStatus(status);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json");
        response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-store");
        response.write(true, ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)), callback);
    }

    private static String quote(String s) {
        return Out.json(s);
    }
}
