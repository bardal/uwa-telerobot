package au.edu.uwa.mech.web;

import au.edu.uwa.mech.devices.camera.ImageData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Everything one session sends to its browser, in order, under one lock, with backpressure:
 * <ul>
 * <li>Images: only from the cameras the browser is showing, and at most one in flight (header and
 *     JPEG as a unit). While one is being written only the newest further frame of each camera is
 *     kept, so a slow browser gets fewer, fresher frames.</li>
 * <li>Robot states: the same, latest wins while one is in flight.</li>
 * <li>Anything else is sent as it comes; if the socket's backlog still passes
 *     {@link #MAX_QUEUED_BYTES} or {@link #MAX_QUEUED_MESSAGES}, {@code overflow} runs once.</li>
 * </ul>
 * Until {@link #open} (the welcome) and after {@link #close}, only {@link #sendNow} gets through.
 */
final class Outbox {
    static final long MAX_QUEUED_BYTES = 2L * 1024 * 1024;
    static final int MAX_QUEUED_MESSAGES = 200;

    private final ClientConnection conn;
    private final Runnable overflow;
    private final AtomicBoolean overflowed = new AtomicBoolean();
    private final Object lock = new Object();

    private volatile boolean open;
    /** The cameras on show in the browser: replaced whole, never changed, so it can be read without the lock. */
    private volatile Set<String> watching = Set.of();

    private boolean imageInFlight;
    /** The newest frame waiting for each camera, the camera that has waited longest first. */
    private final Map<String, ImageData> pending = new LinkedHashMap<>();
    private final Map<String, Integer> seq = new HashMap<>();
    private final Map<String, ImageData> lastSent = new HashMap<>();

    private boolean stateInFlight;
    private Out.RobotState pendingState;

    Outbox(ClientConnection conn, Runnable overflow) {
        this.conn = conn;
        this.overflow = overflow;
    }

    boolean isOpen() {
        return open;
    }

    /**
     * Opens, then sends {@code first} and the snapshot. Live events cannot slip in before them:
     * emit() and the rest take the same lock. The flag goes up before the welcome is written, so
     * a browser that answers the welcome at once is never told to wait for it.
     */
    void open(List<Out> first, Supplier<List<Out>> snapshot) {
        synchronized (lock) {
            open = true;
            first.forEach(conn::send);
            snapshot.get().forEach(conn::send);
        }
        checkBacklog();
    }

    void close() {
        synchronized (lock) {
            open = false;
            pending.clear();
            pendingState = null;
        }
    }

    /** Regardless of open: errors and refusals. */
    void sendNow(Out m) {
        synchronized (lock) {
            conn.send(m);
        }
        checkBacklog();
    }

    /** Only while open. */
    void emit(Out m) {
        synchronized (lock) {
            if (!open) return;
            conn.send(m);
        }
        checkBacklog();
    }

    void robotState(Out.RobotState s) {
        synchronized (lock) {
            if (!open) return;
            if (stateInFlight) {
                pendingState = s;
                return;
            }
            stateInFlight = true;
            conn.send(s, this::stateWritten);
        }
        checkBacklog();
    }

    private void stateWritten() {
        synchronized (lock) {
            Out.RobotState next = pendingState;
            pendingState = null;
            if (next == null || !open) {
                stateInFlight = false;
                return;
            }
            conn.send(next, this::stateWritten);
        }
    }

    /** The cameras whose frames go out. */
    Set<String> watching() {
        return watching;
    }

    boolean watches(String camera) {
        return watching.contains(camera);
    }

    /** One camera only (the forms page, and pages from before several could be shown). */
    void select(String camera, Supplier<Optional<ImageData>> last) {
        watch(List.of(camera), c -> last.get());
    }

    /**
     * A new choice of cameras: from now on only their frames go out, each starting with its
     * {@code last} photo unless that is the frame this browser was sent most recently.
     */
    void watch(Collection<String> cameras, Function<String, Optional<ImageData>> last) {
        synchronized (lock) {
            watching = Collections.unmodifiableSet(new LinkedHashSet<>(cameras));
            pending.keySet().retainAll(watching);
            // read under the lock: a live frame that already went out is the same object (skipped),
            // and one that arrives later waits for us, so an older frame never follows a newer one
            if (open) {
                for (String camera : watching) last.apply(camera).ifPresent(d -> imageLocked(camera, d));
            }
        }
        checkBacklog();
    }

    void image(String camera, ImageData d) {
        synchronized (lock) {
            if (!open || !watching.contains(camera)) return;
            imageLocked(camera, d);
        }
        checkBacklog();
    }

    private void imageLocked(String camera, ImageData d) {
        if (!sendable(d) || lastSent.get(camera) == d) return;
        if (imageInFlight) {
            pending.put(camera, d);
            return;
        }
        imageInFlight = true;
        write(camera, d);
    }

    private void write(String camera, ImageData d) {
        int n = seq.merge(camera, 1, Integer::sum);
        lastSent.put(camera, d);
        conn.sendImage(new Out.Image(camera, n, d.width, d.height, calibration(d)), d.pixels, this::imageWritten);
    }

    private void imageWritten() {
        synchronized (lock) {
            Iterator<Map.Entry<String, ImageData>> waiting = pending.entrySet().iterator();
            while (open && waiting.hasNext()) {
                Map.Entry<String, ImageData> e = waiting.next();
                waiting.remove();
                if (!watching.contains(e.getKey()) || lastSent.get(e.getKey()) == e.getValue()) continue;
                write(e.getKey(), e.getValue());
                return;
            }
            imageInFlight = false;
        }
    }

    /** A frame needs its JPEG and a 3x4 calibration matrix. */
    private static boolean sendable(ImageData d) {
        return d != null && d.pixels != null && d.calibrationMatrix != null
            && d.calibrationMatrix.getRowDimension() == 3 && d.calibrationMatrix.getColumnDimension() == 4;
    }

    private static List<Double> calibration(ImageData d) {
        List<Double> out = new ArrayList<>(12);
        for (double v : d.calibrationMatrix.getRowPackedCopy()) out.add(v);
        return out;
    }

    private void checkBacklog() {
        if ((conn.queuedBytes() > MAX_QUEUED_BYTES || conn.queuedMessages() > MAX_QUEUED_MESSAGES)
            && overflowed.compareAndSet(false, true)) {
            overflow.run();
        }
    }
}
