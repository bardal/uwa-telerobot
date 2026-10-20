package au.edu.uwa.mech.web;

import au.edu.uwa.mech.mom.Channel;
import au.edu.uwa.mech.mom.ContextURL;
import au.edu.uwa.mech.mom.MOMLoginException;
import au.edu.uwa.mech.mom.Token;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Whether the lab is ready for sessions, for {@code /healthz}'s {@code lab}: {@value #READY} once
 * MOM answers and the lab's servers have published themselves in it, else {@value #STARTING}.
 *
 * <p>The MOM check ({@link #mom}) is the gateway's own probe login (as a guest, with a nick no user
 * can take, joining nothing, so nobody sees it) asking MOM whether the robot proxy's
 * {@code robot/robot.token} and {@code robot/robot.channel} and the camera server's
 * {@code cameras/<name>.channel} for every camera exist. RobotProxy creates its token and channel
 * only once it has logged in, and the camera server its channels once it has logged in with its
 * cameras, so that is the moment a session can be welcomed with the robot and all three cameras.
 * Each check is a few MOM round trips on one kept connection; it runs every
 * {@value #STARTING_POLL_MILLIS} ms until ready, then every {@value #READY_POLL_MILLIS} ms.
 */
public final class LabProbe implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(LabProbe.class);

    public static final String READY = "ready";
    public static final String STARTING = "starting";
    static final long STARTING_POLL_MILLIS = 2_000;
    static final long READY_POLL_MILLIS = 15_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** One readiness check: true when ready; false or an exception when not. */
    public interface Check extends AutoCloseable {
        boolean ready() throws Exception;

        @Override
        default void close() { }
    }

    private final Check check;
    private final ScheduledExecutorService timer;
    private volatile boolean ready;
    private volatile boolean closed;

    public LabProbe(Check check) {
        this(check, Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("lab-probe").daemon(true).inheritInheritableThreadLocals(false).factory()));
    }

    /** With its checks scheduled on {@code timer} (tests pass one that runs when they move the clock). */
    LabProbe(Check check, ScheduledExecutorService timer) {
        this.check = check;
        this.timer = timer;
    }

    /** {@value #READY} or {@value #STARTING}, as the last check found. */
    public String state() {
        return ready ? READY : STARTING;
    }

    /** Runs the check now; never throws. True when the lab is ready. */
    public boolean probe() {
        boolean now;
        try {
            now = check.ready();
        } catch (Exception | LinkageError e) {
            LOG.debug("lab probe: {}", e.toString());
            now = false;
        }
        if (now != ready) LOG.info("lab is {}", now ? READY : STARTING);
        ready = now;
        return now;
    }

    /** Checks at once, then on its own thread for as long as the probe is open. */
    public void start() {
        schedule(0);
    }

    private void schedule(long delayMillis) {
        if (closed) return;
        try {
            timer.schedule(() -> {
                boolean ok = probe();
                schedule(ok ? READY_POLL_MILLIS : STARTING_POLL_MILLIS);
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopping) {
            // closed meanwhile
        }
    }

    @Override
    public void close() {
        closed = true;
        timer.shutdownNow();
        try {
            check.close();
        } catch (Exception e) {
            LOG.debug("lab probe close: {}", e.toString());
        }
    }

    // ---- the MOM check -------------------------------------------------------------------------

    /** The contexts the robot proxy and the camera server create, for these camera names. */
    static List<ContextURL> labContexts(List<String> cameras) {
        List<ContextURL> out = new ArrayList<>();
        out.add(new ContextURL("robot", "robot", Token.TYPE_NAME));
        out.add(new ContextURL("robot", "robot", Channel.TYPE_NAME));
        for (String c : cameras) out.add(new ContextURL("cameras", c, Channel.TYPE_NAME));
        return out;
    }

    /** The probe login at {@code host:port} (MOM's guest account) checking {@link #labContexts}. */
    public static Check mom(String host, int port, List<String> cameras) {
        return new MomCheck(host, port, labContexts(cameras));
    }

    static final class MomCheck implements Check {
        private final String url;
        private final List<ContextURL> needed;
        private MomBridge.BridgeRoot root;
        private volatile boolean broken;

        MomCheck(String host, int port, List<ContextURL> needed) {
            // "~" is never in a user's nick (Limits.cleanNick), so no user can collide with the probe
            this.url = "moms://guest:-1:~probe-" + Integer.toString(RANDOM.nextInt(36 * 36 * 36 * 36), 36)
                + "@" + host + ":" + port + "/";
            this.needed = List.copyOf(needed);
        }

        @Override
        public synchronized boolean ready() throws Exception {
            if (root == null || broken || !root.loggedIn()) {
                drop();
                broken = false;
                MomBridge.BridgeRoot r = new MomBridge.BridgeRoot(url, (source, e, direction) -> broken = true);
                try {
                    r.login();
                } catch (MOMLoginException e) {
                    r.forceDisconnect();
                    throw e;
                }
                root = r;
            }
            try {
                for (ContextURL u : needed) {
                    if (!root.exists(u)) {
                        LOG.debug("lab probe: {} does not exist yet", u);
                        return false;
                    }
                }
                return true;
            } catch (Exception | Error e) {
                drop(); // a timeout or a lost socket: log in afresh next time
                throw e;
            }
        }

        private void drop() {
            MomBridge.BridgeRoot r = root;
            root = null;
            if (r != null) r.forceDisconnect();
        }

        @Override
        public synchronized void close() {
            drop();
        }
    }
}
