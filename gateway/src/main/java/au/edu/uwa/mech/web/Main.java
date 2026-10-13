package au.edu.uwa.mech.web;

import au.edu.uwa.mech.util.SerialFilter;
import au.edu.uwa.mech.web.archive.Archive;
import au.edu.uwa.mech.web.archive.ArchiveStore;
import au.edu.uwa.mech.web.archive.FileArchiveStore;
import au.edu.uwa.mech.web.archive.S3ArchiveStore;
import au.edu.uwa.mech.web.cloud.CloudConfig;
import au.edu.uwa.mech.web.cloud.Ec2InstanceControl;
import au.edu.uwa.mech.web.cloud.InstanceControl;
import au.edu.uwa.mech.web.cloud.MemoryParamStore;
import au.edu.uwa.mech.web.cloud.NoopInstanceControl;
import au.edu.uwa.mech.web.cloud.ParamStore;
import au.edu.uwa.mech.web.cloud.SsmParamStore;
import au.edu.uwa.mech.web.dev.InProcessLab;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the gateway and wires everything, for AWS or dev by configuration.
 *
 * <ul>
 *   <li><b>AWS</b> (not {@code --dev}, and {@code ORIGIN_SECRET_PARAM} or {@code TURNSTILE_SECRET_PARAM}
 *       set, as the systemd unit does): secrets, PINs and bans from SSM ({@link CloudConfig}), the
 *       archive in S3 with its byte counter in {@code /telerobot/archive-bytes}, the heartbeat in
 *       SSM, and the instance stops itself 15 minutes after the last session (unless
 *       {@code --no-self-stop}).</li>
 *   <li><b>Dev</b> ({@code --dev}): memory parameters, no self-stop, the archive in
 *       {@code --archive-dir} (default {@code run/archive}), the UI from {@code --static-dir} (default
 *       {@code webui/dist}), Turnstile's always-pass test secret, and optionally the whole lab in
 *       this JVM ({@code --lab-in-process}).</li>
 *   <li>Otherwise (a local production-mode run): settings from flags and environment only.</li>
 * </ul>
 */
public final class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /** Archive cap: past it images stop being saved; chat and move entries continue. */
    public static final long ARCHIVE_CAP_BYTES = 5L * 1024 * 1024 * 1024;
    /** Cloudflare's always-pass Turnstile secret, used by dev mode when no secret is given. */
    public static final String DEV_TURNSTILE_SECRET = "1x0000000000000000000000000000000AA";
    /** The Vite dev server, which proxies /ws to the gateway. */
    static final int VITE_PORT = 5173;

    static final long HEARTBEAT_TICK_SECONDS = 5;
    static final long IDLE_TICK_SECONDS = 30;
    static final long FLUSH_SECONDS = 60;
    static final long PERSIST_SECONDS = 5 * 60;

    private Main() {}

    public static void main(String[] args) {
        Running running;
        try {
            running = start(args, System.getenv());
        } catch (IllegalArgumentException e) {
            System.err.println("gateway: " + e.getMessage());
            System.exit(2);
            return;
        } catch (IllegalStateException e) {
            System.err.println("gateway: " + e.getMessage());
            System.exit(1);
            return;
        } catch (Exception e) {
            System.err.println("gateway: failed to start: " + e);
            e.printStackTrace();
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(running::close, "gateway-shutdown"));
        GatewayConfig c = running.config();
        System.out.println("gateway " + c.commit() + " listening on port " + running.port()
            + (c.dev() ? " (dev" + (c.labInProcess() ? ", lab in process: MOM " + c.momPort() : "") + ")" : ""));
    }

    /** Starts everything; {@link Running#close()} is the shutdown hook's work. */
    public static Running start(String[] args, java.util.Map<String, String> env) throws Exception {
        installSerialFilter();
        GatewayConfig config = GatewayConfig.parse(args, env);
        LongSupplier clock = System::currentTimeMillis;

        if (config.labInProcess()) {
            InProcessLab.Ports ports = config.port() == 0 ? InProcessLab.Ports.free()
                : new InProcessLab.Ports(config.momPort(), 30562, 30563, config.labAdminPort());
            InProcessLab lab = InProcessLab.shared(ports, true);
            config = config.withLab("127.0.0.1", lab.momPort(), lab.adminPort())
                .withSecrets(config.originSecret(), config.turnstileSecret(), lab.gatewayPins());
            LOG.info("lab in process: MOM {}, robot {}/{}, admin {}", lab.momPort(), lab.ports().control(),
                lab.ports().position(), lab.adminPort());
        }
        if (config.dev()) config = devDefaults(config);

        boolean aws = CloudConfig.onAws(config);
        ParamStore params;
        if (aws) {
            params = SsmParamStore.create(CloudConfig.region(config));
            config = CloudConfig.load(config, params);
        } else {
            params = new MemoryParamStore();
        }
        if (!config.dev() && config.turnstileSecret() == null) {
            throw new IllegalStateException("a Turnstile secret is required outside dev mode "
                + "(TURNSTILE_SECRET_PARAM on AWS, else --turnstile-secret or TURNSTILE_SECRET)");
        }
        if (!config.dev() && config.originSecret() == null) {
            throw new IllegalStateException("an origin secret is required outside dev mode "
                + "(ORIGIN_SECRET_PARAM on AWS, else --origin-secret or ORIGIN_SECRET)");
        }

        InstanceControl control = aws && config.instanceStop() ? new Ec2InstanceControl() : new NoopInstanceControl();

        S3ArchiveStore s3 = null;
        ArchiveStore store = null;
        if (aws) {
            ParamStore p = params;
            s3 = S3ArchiveStore.create(config.archiveBucket(), () -> CloudConfig.archiveBytes(p),
                bytes -> p.put(CloudConfig.ARCHIVE_BYTES, Long.toString(bytes)));
            store = s3;
        } else if (config.archiveDir() != null) {
            store = new FileArchiveStore(Path.of(config.archiveDir()));
        } else {
            LOG.warn("no archive: set --archive-dir (or ARCHIVE_BUCKET on AWS)");
        }
        Archive archive = store == null ? null : new Archive(store, clock, ARCHIVE_CAP_BYTES);

        Auth auth = new Auth(config, Turnstile.http(config.turnstileSecret()),
            config.cognitoIssuer() == null ? null : Auth.remoteProcessor(config));
        Idle idle = new Idle(clock, control::stopSelf);
        SessionRegistry registry = new SessionRegistry(config, auth, new Bans(params), idle, clock, archive);
        GatewayServer server = new GatewayServer(config, registry, clock);
        if (archive != null) server.setDevFlush(archive::flush);
        Heartbeat heartbeat = new Heartbeat(params, clock, idle::sessions, idle::endedAt, config.commit());

        server.start();
        heartbeat.tick(); // once at start, then every 60 s
        Running running = new Running(config, server, registry, archive, s3, idle);
        running.schedule(heartbeat);
        return running;
    }

    /** Dev mode's defaults for whatever was not given. */
    static GatewayConfig devDefaults(GatewayConfig c) throws IOException {
        int port = c.port();
        List<String> origins = c.allowedOrigins();
        if (origins.isEmpty()) {
            // the page's own origin, and the Vite dev server's
            if (port == 0) port = freePort();
            origins = List.of("http://localhost:" + port, "http://127.0.0.1:" + port,
                "http://localhost:" + VITE_PORT, "http://127.0.0.1:" + VITE_PORT);
        }
        String staticDir = c.staticDir();
        if (staticDir == null && Files.isDirectory(Path.of("webui", "dist"))) staticDir = "webui/dist";
        String archiveDir = c.archiveDir() == null ? "run/archive" : c.archiveDir();
        GatewayConfig out = c.withServing(port, origins, staticDir, archiveDir);
        if (out.turnstileSecret() == null) out = out.withSecrets(out.originSecret(), DEV_TURNSTILE_SECRET, out.pins());
        return out;
    }

    /**
     * The serialization allowlist the lab servers run with: the gateway's MOM clients deserialise
     * packets from MOM. Left alone if a JVM-wide filter is already set ({@code -Djdk.serialFilter}).
     */
    static synchronized void installSerialFilter() {
        if (ObjectInputFilter.Config.getSerialFilter() != null) return;
        try {
            ObjectInputFilter.Config.setSerialFilter(ObjectInputFilter.Config.createFilter(SerialFilter.PATTERN));
        } catch (IllegalStateException alreadySet) {
            // set meanwhile by someone else
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** A started gateway: its timers, and the shutdown sequence. */
    public static final class Running implements AutoCloseable {
        private final GatewayConfig config;
        private final GatewayServer server;
        private final SessionRegistry registry;
        private final Archive archive;
        private final S3ArchiveStore s3;
        private final Idle idle;
        private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gateway-cron");
            t.setDaemon(true);
            return t;
        });
        private final AtomicBoolean closed = new AtomicBoolean();

        Running(GatewayConfig config, GatewayServer server, SessionRegistry registry, Archive archive,
                S3ArchiveStore s3, Idle idle) {
            this.config = config;
            this.server = server;
            this.registry = registry;
            this.archive = archive;
            this.s3 = s3;
            this.idle = idle;
        }

        public GatewayConfig config() { return config; }

        public int port() { return server.port(); }

        /** Null when nothing is archived. */
        public Archive archive() { return archive; }

        private void schedule(Heartbeat heartbeat) {
            every(HEARTBEAT_TICK_SECONDS, "heartbeat", heartbeat::tick);
            every(IDLE_TICK_SECONDS, "idle", this::idleTick);
            if (archive != null) every(FLUSH_SECONDS, "archive flush", archive::flush);
            if (s3 != null) every(PERSIST_SECONDS, "archive byte counter", s3::persist);
        }

        /** Closes timed-out sessions, then stops the instance if it has been idle 15 minutes. */
        void idleTick() {
            idle.expiredWithReasons().forEach(registry::expire);
            idle.tick();
        }

        private void every(long seconds, String what, Runnable task) {
            timers.scheduleWithFixedDelay(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) { // an escaping exception would silently end the schedule
                    LOG.warn("{} failed", what, e);
                }
            }, seconds, seconds, TimeUnit.SECONDS);
        }

        /**
         * The shutdown: {@code lab stopping} to every session, the archive flushed, the sessions and
         * their bridges closed, the archive byte counter persisted, then the server stopped. Each
         * step runs even if an earlier one failed. Idempotent.
         */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            timers.shutdownNow();
            step("lab stopping", () -> registry.broadcast(new Out.Lab("stopping", "The lab is stopping")));
            if (archive != null) step("archive flush", archive::flush);
            step("closing sessions", registry::shutdown);
            if (s3 != null) step("archive byte counter", s3::persist);
            step("server stop", () -> {
                try {
                    server.stop();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        private static void step(String what, Runnable r) {
            try {
                r.run();
            } catch (RuntimeException e) {
                LOG.warn("shutdown: {} failed", what, e);
            }
        }
    }
}
