package au.edu.uwa.mech.web;

import au.edu.uwa.mech.devices.camera.ImageData;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.maths.Pose3d;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One browser: its identity, nick, {@link Limits}, {@link MomBridge} and {@link Outbox}. Incoming
 * messages are checked on the calling (Jetty) thread, which never blocks; everything that talks to
 * MOM runs on this session's own serial queue of virtual threads, so a slow MOM round trip holds
 * up only this session. Bridge events arrive on this session's MOM threads and go to the Outbox.
 *
 * <p>Once closed, from either side, nothing the browser sends is acted on, and the shutdown
 * (release control, log out of MOM, free the nick) starts at once.
 */
public final class Session implements MomBridge.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    public static final int MAX_QUEUED_COMMANDS = 4;
    public static final int MAX_POINTS_PER_USER = 20;
    public static final int MAX_LABEL_CHARS = 40;
    /** Tasks waiting on the serial queue before the session is considered stuck and closed. */
    static final int MAX_PENDING_TASKS = 200;
    private static final double MAX_COORDINATE = 10_000;

    private final SessionRegistry registry;
    private final ClientConnection conn;
    private final In.Hello hello;
    private final String id;
    private final Limits limits;
    private final Serial serial;
    private final Outbox outbox;

    private volatile Identity identity;
    private volatile String nick;
    private volatile MomBridge bridge;
    private volatile boolean loginDone;
    private volatile boolean closed;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean shutDown = new AtomicBoolean();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger pendingPoints = new AtomicInteger();
    private final AtomicBoolean fetchingSettings = new AtomicBoolean();

    Session(SessionRegistry registry, ClientConnection conn, In.Hello hello, String id) {
        this.registry = registry;
        this.conn = conn;
        this.hello = hello;
        this.id = id;
        this.limits = new Limits(registry.clock());
        this.serial = new Serial(registry.executor());
        this.outbox = new Outbox(conn, () -> close("too-many", "the connection is too slow", 1008));
    }

    public String id() { return id; }

    /** The real nick (after any {@code ~n}), or null before login. */
    public String nick() { return nick; }

    public Identity identity() { return identity; }

    MomBridge bridge() { return bridge; }

    boolean ready() { return outbox.isOpen(); }

    boolean closed() { return closed; }

    // ---- login ---------------------------------------------------------------------------------

    void start() {
        Optional<String> wanted = Limits.cleanNick(hello.nick());
        if (wanted.isEmpty()) {
            refuse("bad-message", "a nickname is 1 to 20 letters, digits, spaces, _ . or -");
            loginDone = true;
            return;
        }
        serial.execute(() -> login(wanted.get()));
    }

    private void login(String wanted) {
        try {
            if (closed) return;
            try {
                registry.logins().acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                if (closed) return; // the tab went while we waited for a login slot
                Identity id;
                try {
                    id = registry.auth().authenticate(hello, conn.remoteIp());
                } catch (Unauthorised e) {
                    LOG.info("hello refused: {}", e.getMessage());
                    refuse("unauthorised", "sign-in failed");
                    return;
                }
                if (registry.bans().banned(id)) {
                    refuse("unauthorised", "you are banned");
                    return;
                }
                String pin = registry.pin(id.level());
                if (pin == null) {
                    LOG.warn("no MOM PIN configured for {}", MomBridge.account(id.level()));
                    refuse("lab-unavailable", "the lab is not available");
                    return;
                }
                if (closed) return;
                identity = id;
                if (!connect(id, wanted, pin)) return;
            } finally {
                registry.logins().release();
            }
            if (closed) return; // the shutdown queued behind us cleans up
            welcome();
        } finally {
            loginDone = true;
        }
    }

    /** Reserves the first free nick, then the next while MOM says it is in use. False if refused. */
    private boolean connect(Identity id, String wanted, String pin) {
        List<String> tried = new ArrayList<>();
        try {
            for (int attempt = 1; ; attempt++) {
                String candidate = registry.nicks().reserve(wanted);
                MomBridge b = new MomBridge(registry.config().momUrlHost(), registry.config().momPort(), id, candidate,
                    pin, this);
                try {
                    b.connect();
                    nick = candidate;
                    bridge = b;
                    return true;
                } catch (MomBridge.NickInUse e) {
                    tried.add(candidate); // held in MOM by someone outside this gateway: keep it out of reach
                    if (attempt >= 20) {
                        refuse("lab-unavailable", "no free nickname");
                        return false;
                    }
                } catch (MomBridge.LabUnavailable e) {
                    LOG.warn("MOM login failed: {}", e.getMessage());
                    registry.nicks().release(candidate);
                    refuse("lab-unavailable", "the lab is not available");
                    return false;
                }
            }
        } finally {
            tried.forEach(registry.nicks()::release);
        }
    }

    private void welcome() {
        MomBridge b = bridge;
        registry.seedWorkspace(this);
        registry.register(this);
        outbox.open(List.of(
            new Out.Welcome(nick, identity.level().name().toLowerCase(Locale.ROOT), b.cameraNames(),
                registry.config().commit(), new Out.Limits(Limits.MAX_SCRIPT_CHARS, Limits.MAX_WAYPOINTS,
                    Limits.MAX_CHAT_CHARS)),
            new Out.Lab("ready", "")), () -> {
                // the state so far; later changes come as live events
                List<Out> snapshot = new ArrayList<>();
                snapshot.add(new Out.Users(b.users()));
                snapshot.add(new Out.Controller(b.controllerNick()));
                RobotState s = b.lastState();
                if (s != null) snapshot.add(toOut(s));
                snapshot.add(new Out.Workspace(b.points()));
                return snapshot;
            });
    }

    private void refuse(String code, String message) {
        close(code, message, 1008);
    }

    // ---- close ---------------------------------------------------------------------------------

    /** The socket has ended. */
    void onClose() {
        closed = true;
        outbox.close();
        beginShutdown();
    }

    /**
     * Server side close (a refusal, kick, ban, idle timeout, lost lab or too many rejections): tell
     * the browser, stop acting on it, and start the shutdown now rather than when the socket ends.
     */
    void close(String code, String message, int wsCode) {
        if (!closed) outbox.sendNow(new Out.Error(code, message));
        closed = true;
        outbox.close();
        conn.close(wsCode, code);
        beginShutdown();
    }

    private void beginShutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) return;
        if (loginDone) {
            registry.executor().execute(this::shutdown); // don't wait behind a slow MOM call on the queue
        } else {
            serial.execute(this::shutdown); // after the login, which may still be connecting
        }
    }

    /** Releases control and logs out of MOM, then frees the nick. */
    private void shutdown() {
        if (!shutDown.compareAndSet(false, true)) return;
        registry.unregister(this);
        MomBridge b = bridge;
        if (b != null) {
            LOG.info("session {} ({}) ended; logging out of MOM", id, b.nick());
            b.close();
            registry.nicks().release(b.nick());
            LOG.debug("session {} ({}) logged out", id, b.nick());
        }
    }

    // ---- incoming ------------------------------------------------------------------------------

    void onBadMessage(BadMessage problem) {
        if (closed) return;
        reject("bad-message", "malformed message");
    }

    void onMessage(In m) {
        if (closed) return;
        if (!limits.allow("any")) {
            reject("rate-limited", "too many messages");
            return;
        }
        if (!outbox.isOpen()) {
            reject("bad-message", "wait for welcome");
            return;
        }
        MomBridge b = bridge;
        boolean accepted = switch (m) {
            case In.Command c -> command(b, c);
            case In.TakeControl t -> run(b::takeControl);
            case In.Release r -> run(b::release);
            case In.Chat c -> chat(b, c);
            case In.SelectCamera s -> selectCamera(b, s);
            case In.CameraSettings s -> cameraSettings(b, s);
            case In.NewImage n -> knownCamera(b, n.camera()) && run(() -> b.newImage(n.camera()));
            case In.WorkspaceAdd w -> workspaceAdd(b, w);
            case In.WorkspaceDelete w -> workspaceDelete(b, w);
            case In.ArchiveImages a -> true; // Task 6
            case In.Activity a -> allow("activity");
            case In.Admin a -> admin(a);
            case In.Hello h -> reject("bad-message", "second hello");
        };
        if (accepted) registry.idle().touch(id);
    }

    private boolean command(MomBridge b, In.Command c) {
        if (!allow("command")) return false;
        Limits.Verdict v = limits.checkScript(c.script());
        if (!v.ok()) return reject("bad-message", v.reason());
        if (!nick.equals(b.controllerNick())) return reject("not-in-control", "take control of the robot first");
        if (inFlight.incrementAndGet() > MAX_QUEUED_COMMANDS) {
            inFlight.decrementAndGet();
            return reject("rate-limited", "at most " + MAX_QUEUED_COMMANDS + " commands may be queued");
        }
        boolean queued = run(() -> {
            String commandId = null;
            try {
                commandId = b.command(c.script());
            } finally {
                if (commandId == null) commandDone(); // refused (closing) or failed before it was tracked
            }
        });
        if (!queued) commandDone();
        return queued;
    }

    private void commandDone() {
        inFlight.updateAndGet(n -> Math.max(0, n - 1));
    }

    private boolean chat(MomBridge b, In.Chat c) {
        if (!allow("chat")) return false;
        Limits.Verdict v = limits.checkChat(c.text());
        if (!v.ok()) return reject("bad-message", v.reason());
        return run(() -> b.chat(v.text()));
    }

    private boolean selectCamera(MomBridge b, In.SelectCamera s) {
        if (!knownCamera(b, s.camera())) return false;
        String camera = s.camera();
        outbox.select(camera, () -> b.lastPhoto(camera));
        Optional<Out.CameraSettings> known = b.lastSettings(camera);
        if (known.isPresent()) {
            outbox.emit(known.get());
        } else if (fetchingSettings.compareAndSet(false, true)) {
            // CameraClient asks the camera server and waits for it: keep that off the session's queue
            registry.executor().execute(() -> {
                try {
                    b.fetchSettings(camera).ifPresent(st -> {
                        if (camera.equals(outbox.selected())) outbox.emit(st);
                    });
                } finally {
                    fetchingSettings.set(false);
                }
            });
        }
        return true;
    }

    private boolean cameraSettings(MomBridge b, In.CameraSettings s) {
        if (!knownCamera(b, s.camera())) return false;
        if (!(s.width() >= 1 && s.width() <= 4096 && s.height() >= 1 && s.height() <= 4096
            && s.zoom() >= 0 && s.zoom() <= 10 && s.quality() >= 0 && s.quality() <= 100)) {
            return reject("bad-message", "camera settings out of range");
        }
        return run(() -> b.cameraSettings(s.camera(), s.on(), (int) Math.round(s.width()), (int) Math.round(s.height()),
            s.zoom(), (int) Math.round(s.quality()), s.fixed()));
    }

    private boolean knownCamera(MomBridge b, String camera) {
        return b.cameraNames().contains(camera) || reject("bad-message", "no such camera");
    }

    private boolean workspaceAdd(MomBridge b, In.WorkspaceAdd w) {
        for (double v : new double[] {w.x(), w.y(), w.z()}) {
            if (Math.abs(v) > MAX_COORDINATE) return reject("bad-message", "point is out of range");
        }
        Limits.Verdict v = limits.checkChat(w.label());
        if (!v.ok()) return reject("bad-message", "label: " + v.reason());
        if (v.text().codePointCount(0, v.text().length()) > MAX_LABEL_CHARS) {
            return reject("bad-message", "label is longer than " + MAX_LABEL_CHARS + " characters");
        }
        // points already added plus adds still on the queue, so a burst can't pass the cap
        long mine = b.points().stream().filter(p -> p.owner().equals(nick)).count();
        if (mine + pendingPoints.incrementAndGet() > MAX_POINTS_PER_USER) {
            pendingPoints.decrementAndGet();
            return reject("rate-limited", "at most " + MAX_POINTS_PER_USER + " points each");
        }
        boolean queued = run(() -> {
            try {
                b.workspaceAdd(w.x(), w.y(), w.z(), v.text());
            } finally {
                pendingPoints.decrementAndGet();
            }
        });
        if (!queued) pendingPoints.decrementAndGet();
        return queued;
    }

    private boolean workspaceDelete(MomBridge b, In.WorkspaceDelete w) {
        Optional<Out.Point> p = b.points().stream().filter(x -> x.id().equals(w.id())).findFirst();
        if (p.isEmpty()) return reject("bad-message", "no such point");
        if (!p.get().owner().equals(nick) && identity.level() != Identity.Level.ADMIN) {
            return reject("unauthorised", "that point is not yours");
        }
        return run(() -> b.workspaceDelete(w.id()));
    }

    private boolean admin(In.Admin a) {
        if (identity.level() != Identity.Level.ADMIN) return reject("unauthorised", "admins only");
        return run(() -> registry.admin(this, a));
    }

    private boolean allow(String bucket) {
        return limits.allow(bucket) || reject("rate-limited", "slow down");
    }

    /** Tells the browser, and closes the session after 20 rejections in a minute. Always false. */
    boolean reject(String code, String message) {
        if (closed) return false;
        outbox.sendNow(new Out.Error(code, message));
        if (limits.recordRejection()) {
            LOG.info("closing session {}: too many rejected messages", id);
            close("rate-limited", "too many rejected messages", 1008);
        }
        return false;
    }

    private boolean run(Runnable task) {
        if (closed) return false;
        if (!serial.execute(task)) {
            LOG.warn("closing session {}: {} tasks pending", id, MAX_PENDING_TASKS);
            close("lab-unavailable", "the lab is not responding", 1011);
            return false;
        }
        return true;
    }

    // ---- outgoing (bridge events, on MOM threads) ----------------------------------------------

    /** Regardless of the welcome: errors from admin actions. */
    void send(Out m) {
        outbox.sendNow(m);
    }

    static Out.RobotState toOut(RobotState s) {
        Pose3d p = s.pose;
        return new Out.RobotState(p.position.x, p.position.y, p.position.z, p.orientation.spinDeg(),
            p.orientation.tiltDeg(), s.gripper.open(), s.moving());
    }

    @Override
    public void console(String text) {
        outbox.emit(new Out.Console(text));
    }

    @Override
    public void robotState(RobotState state) {
        outbox.robotState(toOut(state));
    }

    @Override
    public void controller(String nick) {
        outbox.emit(new Out.Controller(nick));
    }

    @Override
    public void commandStatus(String id, String state, String script, String text) {
        if (state.equals("done") || state.equals("error")) commandDone();
        outbox.emit(new Out.CommandStatus(id, state, script, text));
    }

    @Override
    public void users(List<String> nicks) {
        outbox.emit(new Out.Users(nicks));
    }

    @Override
    public void chat(String nick, String text, long time) {
        outbox.emit(new Out.Chat(chatId(nick, text, time), nick, text, time));
    }

    /** The same id in every session: MOM gives each of them the sender's timestamp. */
    static String chatId(String nick, String text, long time) {
        int h = Math.floorMod((nick + "\n" + text).hashCode(), 36 * 36 * 36 * 36);
        String r = Integer.toString(h, 36);
        return time + "-" + "0".repeat(4 - r.length()) + r;
    }

    @Override
    public void image(String camera, ImageData data) {
        outbox.image(camera, data);
    }

    @Override
    public void cameraSettings(Out.CameraSettings settings) {
        if (settings.camera().equals(outbox.selected())) outbox.emit(settings);
    }

    @Override
    public void workspace(List<Out.Point> points) {
        outbox.emit(new Out.Workspace(points));
    }

    @Override
    public void lost() {
        close("lab-unavailable", "lost the connection to the lab", 1011);
    }

    // ---- serial queue --------------------------------------------------------------------------

    /** Runs tasks one at a time, in order, each on a thread from the base executor. */
    static final class Serial {
        private final Executor base;
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean running;

        Serial(Executor base) {
            this.base = base;
        }

        /** False when too many tasks are waiting. */
        boolean execute(Runnable task) {
            synchronized (this) {
                if (tasks.size() >= MAX_PENDING_TASKS) return false;
                tasks.add(task);
                if (running) return true;
                running = true;
            }
            base.execute(this::drain);
            return true;
        }

        private void drain() {
            while (true) {
                Runnable next;
                synchronized (this) {
                    next = tasks.poll();
                    if (next == null) {
                        running = false;
                        return;
                    }
                }
                try {
                    next.run();
                } catch (RuntimeException e) {
                    LOG.warn("session task failed", e);
                }
            }
        }
    }
}
