package au.edu.uwa.mech.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every browser session, and the {@link SessionHandler} that {@link GatewayServer} calls. Owns the
 * unique nicks, the ban list, the shared virtual-thread executor and the bound on concurrent logins,
 * and carries out admin actions.
 */
public final class SessionRegistry implements SessionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SessionRegistry.class);

    /** Sign-ins (Turnstile or JWKS calls, then the MOM login) running at once; the rest wait their turn. */
    public static final int MAX_CONCURRENT_LOGINS = 16;
    static final int ADMIN_PORT_TIMEOUT_MILLIS = 5_000;

    private final GatewayConfig config;
    private final Auth auth;
    private final Bans bans;
    private final Idle idle;
    private final LongSupplier clock;
    private final Nicks nicks = new Nicks();
    private final Semaphore logins = new Semaphore(MAX_CONCURRENT_LOGINS);
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("session-", 0).inheritInheritableThreadLocals(false).factory());
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public SessionRegistry(GatewayConfig config, Auth auth, Bans bans, Idle idle, LongSupplier clock) {
        this.config = config;
        this.auth = auth;
        this.bans = bans;
        this.idle = idle;
        this.clock = clock;
    }

    // ---- SessionHandler ------------------------------------------------------------------------

    @Override
    public void onHello(ClientConnection connection, In.Hello hello) {
        Session s = new Session(this, connection, hello, "s" + ids.incrementAndGet());
        connection.attach(s);
        s.start();
    }

    @Override
    public void onMessage(ClientConnection connection, In message) {
        if (connection.attachment() instanceof Session s) s.onMessage(message);
    }

    @Override
    public void onBadMessage(ClientConnection connection, BadMessage problem) {
        if (connection.attachment() instanceof Session s) s.onBadMessage(problem);
    }

    @Override
    public void onClose(ClientConnection connection) {
        if (connection.attachment() instanceof Session s) s.onClose();
    }

    // ---- sessions ------------------------------------------------------------------------------

    void register(Session s) {
        sessions.put(s.id(), s);
        idle.opened(s.id());
    }

    void unregister(Session s) {
        if (sessions.remove(s.id()) != null) idle.closed(s.id());
    }

    /** Welcomed, open sessions. */
    public List<Session> sessions() {
        return List.copyOf(sessions.values());
    }

    public Optional<Session> byNick(String nick) {
        return sessions.values().stream().filter(s -> nick != null && nick.equals(s.nick())).findFirst();
    }

    /** Closes a session from the server side, for example after the idle timeout (Task 7). */
    public void close(String sessionId, String code, String message) {
        Session s = sessions.get(sessionId);
        if (s != null) s.close(code, message, 1008);
    }

    /** Points other sessions already know go into a new session's workspace. */
    void seedWorkspace(Session target) {
        for (Session s : sessions.values()) {
            MomBridge b = s.bridge();
            if (s != target && b != null) {
                target.bridge().seedWorkspace(b.objects());
                return;
            }
        }
    }

    /** Logs every bridge out of MOM. */
    public void shutdown() {
        for (Session s : sessions()) s.close("lab-unavailable", "the gateway is stopping", 1001);
        for (Session s : sessions()) {
            MomBridge b = s.bridge();
            if (b != null) b.close();
        }
        executor.shutdown();
    }

    // ---- admin ---------------------------------------------------------------------------------

    /** Runs on the admin session's queue; the caller has checked the level. */
    void admin(Session from, In.Admin a) {
        switch (a.action()) {
            case "kick", "ban" -> {
                Optional<Session> target = byNick(a.nick());
                if (target.isEmpty()) {
                    from.reject("bad-message", "no such user");
                    return;
                }
                Session t = target.get();
                if (a.action().equals("ban")) {
                    bans.ban(t.identity());
                    LOG.info("{} banned {}", from.nick(), t.nick());
                    t.close("unauthorised", "you are banned", 1008);
                } else {
                    LOG.info("{} kicked {}", from.nick(), t.nick());
                    t.close("unauthorised", "removed by an administrator", 1008);
                }
                from.console((a.action().equals("ban") ? "Banned " : "Kicked ") + t.nick());
            }
            case "reset" -> {
                if (resetLab()) {
                    LOG.info("{} reset the lab", from.nick());
                    from.console("The lab has been reset");
                } else {
                    from.send(new Out.Error("lab-unavailable", "the lab did not reset"));
                }
            }
            default -> from.reject("bad-message", "not available yet"); // archive deletes: Task 6
        }
    }

    /** VirtualLab's admin port: {@code reset} is answered {@code ok}. */
    boolean resetLab() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(config.momUrlHost(), config.labAdminPort()), ADMIN_PORT_TIMEOUT_MILLIS);
            s.setSoTimeout(ADMIN_PORT_TIMEOUT_MILLIS);
            OutputStream out = s.getOutputStream();
            out.write("reset\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String reply = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII))
                .readLine();
            return "ok".equals(reply == null ? null : reply.trim());
        } catch (IOException e) {
            LOG.warn("lab reset failed: {}", e.getMessage());
            return false;
        }
    }

    // ---- for sessions --------------------------------------------------------------------------

    /** The MOM PIN of the account for a level, or null when it is not configured. */
    String pin(Identity.Level level) {
        return switch (level) {
            case GUEST -> "-1";
            case REGISTERED -> config.pins().get(GatewayConfig.PIN_REGISTERED);
            case ADMIN -> config.pins().get(GatewayConfig.PIN_ADMIN);
        };
    }

    GatewayConfig config() { return config; }
    Auth auth() { return auth; }
    Bans bans() { return bans; }
    Idle idle() { return idle; }
    LongSupplier clock() { return clock; }
    Nicks nicks() { return nicks; }
    Semaphore logins() { return logins; }
    ExecutorService executor() { return executor; }
}
