package au.edu.uwa.mech.web;

import au.edu.uwa.mech.devices.camera.Camera;
import au.edu.uwa.mech.devices.camera.CameraException;
import au.edu.uwa.mech.devices.camera.CameraManagerClient;
import au.edu.uwa.mech.devices.camera.CameraSettings;
import au.edu.uwa.mech.devices.camera.ImageData;
import au.edu.uwa.mech.devices.robot.Client;
import au.edu.uwa.mech.devices.robot.Command;
import au.edu.uwa.mech.devices.robot.MMoveCommand;
import au.edu.uwa.mech.devices.robot.RobotException;
import au.edu.uwa.mech.devices.robot.RobotHandler;
import au.edu.uwa.mech.devices.robot.RobotListener;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.devices.robot.StateHandler;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.gui.overlaid.OverlaidPoint;
import au.edu.uwa.mech.maths.Point3d;
import au.edu.uwa.mech.model.WorkspaceClient;
import au.edu.uwa.mech.model.WorkspaceObject;
import au.edu.uwa.mech.mom.Channel;
import au.edu.uwa.mech.mom.Context;
import au.edu.uwa.mech.mom.ContextURL;
import au.edu.uwa.mech.mom.MOMException;
import au.edu.uwa.mech.mom.MOMLoginException;
import au.edu.uwa.mech.mom.MOMPacket;
import au.edu.uwa.mech.mom.ManageableTransceiver;
import au.edu.uwa.mech.mom.Peer;
import au.edu.uwa.mech.mom.SessionRoot;
import au.edu.uwa.mech.mom.TransceiverManager;
import au.edu.uwa.mech.mom.event.MessageEventListener;
import au.edu.uwa.mech.mom.event.PeerEventListener;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One browser session's view of the lab, built from the 2000 client classes exactly as the
 * TA200001 applet wires them: a MOM {@link SessionRoot}, the robot {@link Client}, a
 * {@link CameraManagerClient}, the {@code chat.channel} and the {@link WorkspaceClient} on
 * {@code workspace.channel}.
 *
 * <p>Threads: {@link #connect()}, {@link #close()} and the commands are called by the owning
 * session one at a time (they may block on MOM round trips). The {@link Listener} is called on
 * this bridge's own MOM socket threads, never another bridge's, and must not block.
 *
 * <p>The 2000 code prints to the global {@link TheConsole}. Each bridge marks its threads (and the
 * threads they start, such as the MOM socket readers) with an inheritable thread local, and a
 * single router installed here hands each line to the bridge whose thread printed it.
 */
public final class MomBridge {
    private static final Logger LOG = LoggerFactory.getLogger(MomBridge.class);

    /** Round-trip and logout limits for MOM requests made by the gateway itself. */
    static final long SYNC_MILLIS = 5_000;
    static final long LOGOUT_MILLIS = 5_000;
    /** How long to wait for Client's "had a problem" line after a command goes to ERROR. */
    static final long ERROR_TEXT_MILLIS = 1_000;

    private static final ContextURL CHAT = new ContextURL("chat.channel");
    private static final ContextURL WORKSPACE = new ContextURL("workspace.channel");
    /** Never created: an exists request for it is a round trip that orders everything sent before it. */
    private static final ContextURL SYNC = new ContextURL("gateway-sync.channel");
    private static final Pattern PROBLEM = Pattern.compile("Command (\\d+) had a problem :(.*)", Pattern.DOTALL);
    private static final Pattern COMPLETED = Pattern.compile("Command \\d+ completed successfully");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final InheritableThreadLocal<MomBridge> CURRENT = new InheritableThreadLocal<>();
    private static final ScheduledExecutorService TIMERS = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("bridge-timers").daemon(true).inheritInheritableThreadLocals(false).factory());

    static {
        TheConsole.setConsole(text -> {
            MomBridge b = CURRENT.get();
            if (b != null) b.consoleLine(text);
            else LOG.debug("lab console: {}", text);
        });
        // SessionRoot.login logs "Logging in user:PIN:nick"; the PINs must not reach the gateway's log
        TheLog.setLog(line -> LOG.debug("lab: {}", line.startsWith("Logging in ") ? "Logging in (details hidden)" : line));
    }

    /** What the bridge reports. Called on MOM threads; implementations must be quick and thread-safe. */
    public interface Listener {
        void robotState(RobotState state);

        /** The robot's controller, or null when nobody has control. */
        void controller(String nick);

        /** {@code state} is queued, sent, done or error. */
        void commandStatus(String id, String state, String script, String text);

        void users(List<String> nicks);

        void chat(String nick, String text, long time);

        void image(String camera, ImageData data);

        void cameraSettings(Out.CameraSettings settings);

        void workspace(List<Out.Point> points);

        /** A lab message the browser cannot build itself (robot results and errors, camera refusals). */
        void console(String text);

        /** The MOM connection has gone. */
        void lost();
    }

    /** MOM already has a peer with this nick. */
    public static final class NickInUse extends Exception {
        NickInUse(String nick) {
            super("nick in use: " + nick);
        }
    }

    /** MOM refused the login or could not be reached. */
    public static final class LabUnavailable extends Exception {
        LabUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final String host;
    private final int port;
    private final Identity identity;
    private final String nick;
    private final String pin;
    private final Listener out;

    private BridgeRoot root;
    private BridgeClient client;
    private CameraManagerClient cameraManager;
    private volatile Channel chat;
    private volatile WorkspaceClient workspace;
    private volatile boolean closing;
    private final java.util.concurrent.atomic.AtomicBoolean lost = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean closeStarted = new java.util.concurrent.atomic.AtomicBoolean();
    /** Orders takeControl and command against close: none of them overlap. */
    private final Object controlLock = new Object();
    private volatile RobotState lastState;

    private final Map<String, Camera> cameras = new ConcurrentHashMap<>();
    private final Map<String, ImageData> lastFrames = new ConcurrentHashMap<>();
    private final Map<String, Out.CameraSettings> lastSettings = new ConcurrentHashMap<>();
    private final Map<Integer, Track> commands = new ConcurrentHashMap<>();

    public MomBridge(String momHost, int momPort, Identity id, String nick, String pin, Listener out) {
        for (String part : new String[] {nick, pin}) {
            // the URL is user:pin:nick@host:port/, split on those characters
            if (part == null || part.isEmpty() || part.matches(".*[:@/\\p{Cntrl}].*")) {
                throw new IllegalArgumentException("nick and pin must be non-empty without : @ / or control characters");
            }
        }
        this.host = momHost;
        this.port = momPort;
        this.identity = id;
        this.nick = nick;
        this.pin = pin;
        this.out = out;
    }

    /** The MOM service account for a trust level. */
    public static String account(Identity.Level level) {
        return switch (level) {
            case GUEST -> "guest";
            case REGISTERED -> "registered";
            case ADMIN -> "admin";
        };
    }

    String url() {
        return "moms://" + account(identity.level()) + ":" + pin + ":" + nick + "@" + host + ":" + port + "/";
    }

    public String nick() {
        return nick;
    }

    // ---- connect and close ---------------------------------------------------------------------

    /**
     * Logs in to MOM and joins everything the applet joins. Blocks until the robot, cameras, chat and
     * workspace are known, so the caller can send a complete welcome.
     */
    public void connect() throws NickInUse, LabUnavailable {
        MomBridge previous = CURRENT.get();
        CURRENT.set(this); // the MOM socket threads started by login inherit this
        try {
            root = new BridgeRoot(url(), (source, e, direction) -> connectionError(e));
            client = new BridgeClient(root);
            client.addRobotListener(new RobotEvents());
            cameraManager = new CameraManagerClient(root);
            cameraManager.addCameraManagerListener((manager, camera) -> newCamera(camera));
            try {
                root.login();
            } catch (MOMLoginException e) {
                root.forceDisconnect();
                if (String.valueOf(e.getMessage()).contains("already in use")) throw new NickInUse(nick);
                throw new LabUnavailable("MOM refused the login", e);
            }
            // root children (robot, cameras, chat, workspace) -> their joins -> their children's joins
            root.sync();
            root.sync();
            Channel c = (Channel) root.lookupOrCreate(CHAT);
            ChatEvents events = new ChatEvents();
            c.addMessageEventListener(events);
            c.addPeerEventListener(events);
            chat = c;
            c.join();
            WorkspaceClient w = new WorkspaceClient((Channel) root.lookupOrCreate(WORKSPACE));
            w.model.observable().addObserver((o, arg) -> out.workspace(points()));
            workspace = w;
            root.sync(); // token, channel, camera, chat and workspace joins are all answered
        } catch (MOMException e) {
            if (root != null) root.forceDisconnect();
            throw new LabUnavailable("MOM error during login", e);
        } finally {
            restore(previous);
        }
    }

    /** Releases control if this session holds it, then logs out of MOM. Idempotent. */
    public void close() {
        // after this, takeControl and command do nothing; one already sending finishes first
        synchronized (controlLock) {
            if (!closeStarted.compareAndSet(false, true)) return;
            closing = true;
        }
        within(() -> {
            if (root == null || !root.loggedIn()) return null;
            // Release whatever our local view says: a grab of ours may still be on its way to MOM, or
            // its GRABBED event on its way back. MOM handles our packets in order, so this release
            // follows any grab we sent; if someone else owns the token MOM refuses it harmlessly.
            if (client.tokenKnown()) {
                try {
                    client.releaseControl();
                } catch (RobotException | RuntimeException e) {
                    LOG.debug("release on close failed: {}", e.getMessage());
                }
            }
            // MOM answers a logout at once (then waits 10 s before dropping the socket), and the
            // release above is ahead of it on the same socket. Never hang on a dead MOM.
            Thread logout = Thread.ofVirtual().name("mom-logout-" + nick).start(() -> {
                try {
                    root.logout();
                } catch (Exception e) {
                    LOG.debug("logout failed: {}", e.getMessage());
                }
            });
            try {
                if (!logout.join(java.time.Duration.ofMillis(LOGOUT_MILLIS))) root.forceDisconnect();
            } catch (InterruptedException e) {
                root.forceDisconnect();
                Thread.currentThread().interrupt();
            }
            return null;
        });
    }

    private void connectionError(Exception e) {
        if (closing || !lost.compareAndSet(false, true)) return;
        LOG.info("MOM connection for {} lost: {}", nick, e.getMessage());
        out.lost();
    }

    // ---- robot ---------------------------------------------------------------------------------

    /**
     * Sends a path script as the applet's Send button does; returns its id, or null when the bridge
     * is closing (nothing is sent). Status comes through the listener.
     */
    public String command(String script) {
        return within(() -> {
            synchronized (controlLock) {
                if (closing) return null;
                MMoveCommand c = new MMoveCommand(script);
                Track t = new Track(Integer.toString(c.ID()), script);
                commands.put(c.ID(), t);
                c.observable().addObserver((o, arg) -> commandChanged(c, t));
                out.commandStatus(t.id, "queued", script, "");
                try {
                    client.send(c);
                } catch (MOMException e) {
                    t.finish("error", "Error sending commands :" + e.getMessage());
                }
                return t.id;
            }
        });
    }

    /** Does nothing once the bridge is closing, so close() can't miss a grab and leave the token held. */
    public void takeControl() {
        within(() -> {
            synchronized (controlLock) {
                if (closing) return null;
                try {
                    client.gainControl();
                } catch (RobotException e) {
                    out.console(e.getMessage());
                }
            }
            return null;
        });
    }

    public void release() {
        within(() -> {
            try {
                client.releaseControl();
            } catch (RobotException e) {
                out.console(e.getMessage());
            }
            return null;
        });
    }

    /** The controller's nick as MOM last told us, or null. */
    public String controllerNick() {
        BridgeClient c = client;
        if (c == null || !c.hasController()) return null;
        try {
            Peer p = c.getController();
            return p == null ? null : p.getNickname();
        } catch (RobotException | RuntimeException e) {
            return null;
        }
    }

    /** The last robot state, or null before the first one. */
    public RobotState lastState() {
        return lastState;
    }

    private void commandChanged(Command c, Track t) {
        switch (c.state()) {
            case Command.SENDING, Command.EXECUTING -> t.sent();
            case Command.COMPLETED -> t.finish("done", "");
            case Command.ERROR ->
                // Client prints the robot's reason just after this; wait for it (consoleLine), but not forever
                TIMERS.schedule(() -> t.finish("error", "the command failed"), ERROR_TEXT_MILLIS, TimeUnit.MILLISECONDS);
            default -> { }
        }
    }

    /** A command's way to done or error. Each state is reported once, and nothing after the end. */
    private final class Track {
        final String id;
        final String script;
        private boolean sent;
        private boolean finished;

        Track(String id, String script) {
            this.id = id;
            this.script = script;
        }

        synchronized void sent() {
            if (sent || finished) return;
            sent = true;
            out.commandStatus(id, "sent", script, "");
        }

        synchronized void finish(String state, String text) {
            if (finished) return;
            finished = true;
            commands.values().remove(this);
            out.commandStatus(id, state, script, text);
        }
    }

    /** One line from TheConsole printed on this bridge's threads. */
    void consoleLine(String text) {
        Matcher m = PROBLEM.matcher(text);
        if (m.matches()) {
            Track t = commands.get(Integer.parseInt(m.group(1)));
            if (t != null) {
                t.finish("error", m.group(2).trim()); // the browser prints "Command N had a problem :text" itself
                return;
            }
        }
        // the browser builds these from commandStatus and cameraSettings
        if (text.startsWith("Sending command ") || COMPLETED.matcher(text).matches()
            || text.startsWith("New settings for camera ")) {
            return;
        }
        out.console(text);
    }

    private final class RobotEvents implements RobotListener {
        @Override public void setConnected(StateHandler source, boolean connected) { }

        @Override
        public void newRobotState(StateHandler source, RobotState state) {
            // Client calls a RobotListener twice with the same object (it is also a StateListener)
            if (state == lastState) return;
            lastState = state;
            out.robotState(state);
        }

        @Override
        public void newController(RobotHandler source, String name, boolean us) {
            out.controller(name);
        }

        @Override
        public void noController(RobotHandler source) {
            out.controller(null);
        }
    }

    // ---- chat and users ------------------------------------------------------------------------

    /** Sends chat text on chat.channel exactly as ChatPanel does. */
    public void chat(String text) {
        within(() -> {
            Channel c = chat;
            if (c == null) return null;
            try {
                c.sendMessage(c.getPacketFactory().message(text.toCharArray()));
            } catch (MOMException e) {
                out.console("Failed to to send message : " + e.getMessage());
            }
            return null;
        });
    }

    /** Nicks in chat.channel, as ChatPanel's Users list. */
    public List<String> users() {
        Channel c = chat;
        if (c == null) return List.of();
        List<String> nicks = new ArrayList<>();
        for (Object p : c.getPeers().toArray()) {
            String n = p instanceof Peer peer ? peer.getNickname() : null;
            if (n != null && !nicks.contains(n)) nicks.add(n);
        }
        return nicks;
    }

    private final class ChatEvents implements MessageEventListener, PeerEventListener {
        @Override
        public void message(Context c, MOMPacket m) {
            if (!(m.getArg() instanceof char[] chars)) return;
            Peer sender = m.getSender();
            Long time = m.getTimestamp();
            out.chat(sender == null ? "?" : sender.getNickname(), new String(chars),
                time == null ? System.currentTimeMillis() : time);
        }

        @Override public void joined(Context c) { }
        @Override public void left(Context c) { }
        @Override public void peerJoined(Context c, Peer p) { out.users(users()); }
        @Override public void peerLeft(Context c, Peer p) { out.users(users()); }
    }

    // ---- cameras -------------------------------------------------------------------------------

    /** Camera names, sorted. */
    public List<String> cameraNames() {
        return cameras.keySet().stream().sorted().toList();
    }

    public Optional<ImageData> lastPhoto(String camera) {
        ImageData d = lastFrames.get(camera);
        if (d == null) {
            Camera c = cameras.get(camera);
            if (c != null) d = c.lastPhoto();
        }
        return Optional.ofNullable(d).filter(x -> x.pixels != null);
    }

    /** The latest frame of every camera (for the archive, Task 6). */
    public Map<String, ImageData> lastFrames() {
        return Map.copyOf(lastFrames);
    }

    public Optional<Out.CameraSettings> lastSettings(String camera) {
        return Optional.ofNullable(lastSettings.get(camera));
    }

    /** Asks the camera server (CameraClient.getSettings blocks until it answers). */
    public Optional<Out.CameraSettings> fetchSettings(String camera) {
        return within(() -> {
            Camera c = cameras.get(camera);
            if (c == null || closing) return Optional.<Out.CameraSettings>empty();
            CameraSettings s = c.getSettings();
            if (s == null) return Optional.<Out.CameraSettings>empty();
            Out.CameraSettings out = new Out.CameraSettings(camera, c.getOn(), s.getWidth(), s.getHeight(),
                s.getZoom(), s.getQuality(), s.getFixed());
            lastSettings.putIfAbsent(camera, out);
            return Optional.of(out);
        });
    }

    public void newImage(String camera) {
        within(() -> {
            Camera c = cameras.get(camera);
            if (c == null) return null;
            try {
                c.takePhoto();
            } catch (CameraException e) {
                out.console(e.getMessage());
            }
            return null;
        });
    }

    /** As CameraControlsPanel: turn the camera on or off, then change its settings. */
    public void cameraSettings(String camera, boolean on, int width, int height, double zoom, int quality, boolean fixed) {
        within(() -> {
            Camera c = cameras.get(camera);
            if (c == null) return null;
            c.setOn(on);
            if (!on) return null;
            CameraSettings s = c.getSettings();
            if (s == null) return null;
            s.setWidth(width);
            s.setHeight(height);
            s.setZoom((float) zoom);
            s.setQuality(quality);
            s.setFixed(fixed);
            c.setSettings(s);
            return null;
        });
    }

    private void newCamera(Camera camera) {
        String name = camera.getName();
        cameras.put(name, camera);
        camera.addImageListener((source, image) -> {
            if (image == null || image.pixels == null) return;
            lastFrames.put(name, image);
            out.image(name, image);
        });
        camera.addCameraListener((source, settings) -> {
            Out.CameraSettings s = new Out.CameraSettings(name, camera.getOn(), settings.getWidth(),
                settings.getHeight(), settings.getZoom(), settings.getQuality(), settings.getFixed());
            lastSettings.put(name, s);
            out.cameraSettings(s);
        });
    }

    // ---- workspace -----------------------------------------------------------------------------

    /** Adds a point owned by this nick; returns its id (the workspace object's name). */
    public String workspaceAdd(double x, double y, double z, String label) {
        return within(() -> {
            WorkspaceClient w = workspace;
            if (w == null) return null;
            OverlaidPoint p = new OverlaidPoint(x, y, z);
            p.setName(label);
            WorkspaceObject wo = new WorkspaceObject();
            wo.name = "p" + System.currentTimeMillis() + "-" + Integer.toString(RANDOM.nextInt(36 * 36 * 36 * 36), 36);
            wo.creator = nick;
            wo.created = new Date();
            wo.overlay = p;
            try {
                w.addObject(wo);
            } catch (MOMException e) {
                out.console(e.getMessage());
            }
            return wo.name;
        });
    }

    public void workspaceDelete(String id) {
        within(() -> {
            WorkspaceClient w = workspace;
            if (w == null) return null;
            try {
                w.deleteObject(id);
            } catch (MOMException e) {
                out.console(e.getMessage());
            }
            return null;
        });
    }

    /** The current points, sorted by id. */
    public List<Out.Point> points() {
        WorkspaceClient w = workspace;
        if (w == null) return List.of();
        List<Out.Point> points = new ArrayList<>();
        for (WorkspaceObject wo : objects()) {
            if (wo.overlay instanceof OverlaidPoint p) {
                Point3d at = p.getPosition();
                String label = p.getName() == null ? wo.name : p.getName();
                points.add(new Out.Point(wo.name, at.x, at.y, at.z, label, wo.creator == null ? "" : wo.creator));
            }
        }
        points.sort(java.util.Comparator.comparing(Out.Point::id));
        return points;
    }

    /** The workspace objects this bridge knows. */
    public List<WorkspaceObject> objects() {
        WorkspaceClient w = workspace;
        if (w == null) return List.of();
        List<WorkspaceObject> all = new ArrayList<>();
        for (Enumeration<?> e = w.model.objectNames(); e.hasMoreElements(); ) {
            WorkspaceObject wo = w.model.get((String) e.nextElement());
            if (wo != null) all.add(wo);
        }
        return all;
    }

    /**
     * Copies points other sessions already hold into this bridge's model. MOM only relays changes, so
     * without this a new session would not see points added before it joined.
     */
    public void seedWorkspace(Collection<WorkspaceObject> known) {
        WorkspaceClient w = workspace;
        if (w == null) return;
        for (WorkspaceObject wo : known) {
            if (wo.name != null && wo.overlay != null && w.model.get(wo.name) == null) w.model.addObject(wo.name, wo);
        }
    }

    // ---- plumbing ------------------------------------------------------------------------------

    /** Runs on the caller's thread marked as this bridge's, so TheConsole lines and new threads are ours. */
    private <T> T within(Supplier<T> body) {
        MomBridge previous = CURRENT.get();
        CURRENT.set(this);
        try {
            return body.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(MomBridge previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    /** SessionRoot with a connection-error hook, a bounded round trip and a forced disconnect. */
    static final class BridgeRoot extends SessionRoot {
        BridgeRoot(String url, TransceiverManager onError) throws MOMException {
            super(url);
            if (connection instanceof ManageableTransceiver m) m.setManager(onError);
        }

        /** A request MOM answers after everything sent before it, so events it caused have arrived. */
        void sync() throws MOMException {
            sendAndReceive(getPacketFactory().existsRequest(SYNC), SYNC_MILLIS);
        }

        void forceDisconnect() {
            try {
                connection.disconnect();
            } catch (MOMException | RuntimeException e) {
                LOG.debug("disconnect failed", e);
            }
        }
    }

    /**
     * The robot Client, sending each command straight away. Client.sendCommands walks a CommandList
     * numbered from 1, but Command ids are global to the JVM, so with several bridges in one process
     * that walk hits missing ids; sendCommand is the same send without the walk.
     */
    static final class BridgeClient extends Client {
        BridgeClient(SessionRoot root) {
            super(root);
        }

        void send(Command c) throws MOMException {
            sendCommand(c);
        }

        /** True once the robot token is known locally (a grab can only have been sent then). */
        boolean tokenKnown() {
            return robotToken != null;
        }
    }

}
