package au.edu.uwa.mech.web;

import au.edu.uwa.mech.web.archive.Archive;
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
    private final Archive archive;
    private final Nicks nicks = new Nicks();
    private final Semaphore logins = new Semaphore(MAX_CONCURRENT_LOGINS);
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("session-", 0).inheritInheritableThreadLocals(false).factory());
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public SessionRegistry(GatewayConfig config, Auth auth, Bans bans, Idle idle, LongSupplier clock) {
        this(config, auth, bans, idle, clock, null);
    }

    /** {@code archive} may be null: nothing is archived (and archiveImages is refused). */
    public SessionRegistry(GatewayConfig config, Auth auth, Bans bans, Idle idle, LongSupplier clock, Archive archive) {
        this.config = config;
        this.archive = archive;
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
        // nicks are unique ignoring case (Nicks), so match the same way
        return sessions.values().stream().filter(s -> nick != null && nick.equalsIgnoreCase(s.nick())).findFirst();
    }

    /** Closes a session from the server side with an error. */
    public void close(String sessionId, String code, String message) {
        Session s = sessions.get(sessionId);
        if (s != null) s.close(code, message, 1008);
    }

    /** Closes a session that timed out: {@code lab stopping} with the reason, then a normal close. */
    public void expire(String sessionId, String message) {
        Session s = sessions.get(sessionId);
        if (s != null) s.end(new Out.Lab("stopping", message), 1000, "idle");
    }

    /** Sends a message to every welcomed session (the shutdown's {@code lab stopping}). */
    public void broadcast(Out message) {
        for (Session s : sessions()) s.send(message);
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

    /** Closes every session (going away) and logs every bridge out of MOM. */
    public void shutdown() {
        for (Session s : sessions()) s.end(null, 1001, "stopping");
        for (Session s : sessions()) {
            MomBridge b = s.bridge();
            if (b != null) b.close();
        }
        executor.shutdown();
        try {
            // the per-session logouts started by the closes above
            executor.awaitTermination(MomBridge.LOGOUT_MILLIS + 1_000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
                    // every live session with that sub or address goes, not only the named one
                    for (Session s : sessions()) {
                        if (s != from && s.identity() != null && bans.banned(s.identity())) {
                            s.close("unauthorised", "you are banned", 1008);
                        }
                    }
                    if (bans.banned(from.identity())) from.console("Note: your own address is now banned too");
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
            case "deleteMove", "deleteSnapshot", "deleteChat" -> {
                String kind = switch (a.action()) {
                    case "deleteMove" -> "move";
                    case "deleteSnapshot" -> "snapshot";
                    default -> "chat";
                };
                if (archive == null) {
                    from.send(new Out.Error("lab-unavailable", "there is no archive"));
                    return;
                }
                if (a.id() == null || !archive.delete(kind, a.id())) {
                    from.reject("bad-message", "no such " + kind + " in the archive");
                    return;
                }
                LOG.info("{} deleted archived {} {}", from.nick(), kind, a.id());
                from.console("Deleted " + kind + " " + a.id());
            }
            default -> from.reject("bad-message", "unknown admin action");
        }
    }

    /**
     * A block command from the controller or an admin (the session has checked). The lab answers
     * {@code ok <what happened>}, told to everyone, or {@code error <why not>}, told to the asker.
     */
    void blocks(Session from, String action) {
        String reply = labAdmin("blocks " + action);
        if (reply == null) {
            from.send(new Out.Error("lab-unavailable", "the lab did not answer"));
        } else if (reply.startsWith("ok ")) {
            LOG.info("{} blocks {}: {}", from.nick(), action, reply);
            broadcast(new Out.Console(from.nick() + " " + reply.substring(3)));
        } else {
            from.console(reply.startsWith("error ") ? reply.substring(6) : reply);
        }
    }

    /** VirtualLab's admin port: {@code reset} is answered {@code ok}. */
    boolean resetLab() {
        return "ok".equals(labAdmin("reset"));
    }

    /** One line to VirtualLab's admin port and its one-line answer, trimmed; null if the lab could not be reached. */
    String labAdmin(String line) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(config.momUrlHost(), config.labAdminPort()), ADMIN_PORT_TIMEOUT_MILLIS);
            s.setSoTimeout(ADMIN_PORT_TIMEOUT_MILLIS);
            OutputStream out = s.getOutputStream();
            out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String reply = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII))
                .readLine();
            return reply == null ? null : reply.trim();
        } catch (IOException e) {
            LOG.warn("lab admin '{}' failed: {}", line, e.getMessage());
            return null;
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
    /** Null when nothing is archived. */
    Archive archive() { return archive; }
    LongSupplier clock() { return clock; }
    Nicks nicks() { return nicks; }
    Semaphore logins() { return logins; }
    ExecutorService executor() { return executor; }
}
