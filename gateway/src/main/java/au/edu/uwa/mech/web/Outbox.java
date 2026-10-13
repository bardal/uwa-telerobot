package au.edu.uwa.mech.web;

import au.edu.uwa.mech.devices.camera.ImageData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Everything one session sends to its browser, in order, under one lock, with backpressure:
 * <ul>
 * <li>Images: at most one in flight (header and JPEG as a unit). While one is being written only
 *     the newest further frame is kept, so a slow browser gets fewer, fresher frames.</li>
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
    private volatile String selected;

    private boolean imageInFlight;
    private String pendingCamera;
    private ImageData pendingImage;
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

    /** Sends {@code first}, opens, then sends the snapshot, so no live event slips in before it. */
    void open(List<Out> first, Supplier<List<Out>> snapshot) {
        synchronized (lock) {
            first.forEach(conn::send);
            open = true;
            snapshot.get().forEach(conn::send);
        }
        checkBacklog();
    }

    void close() {
        synchronized (lock) {
            open = false;
            pendingImage = null;
            pendingCamera = null;
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

    String selected() {
        return selected;
    }

    /** A new camera choice: from now on only its frames go out, starting with {@code last}. */
    void select(String camera, Supplier<Optional<ImageData>> last) {
        synchronized (lock) {
            selected = camera;
            if (pendingCamera != null && !pendingCamera.equals(camera)) {
                pendingCamera = null;
                pendingImage = null;
            }
            // read under the lock: a live frame that already went out is the same object (skipped),
            // and one that arrives later waits for us, so an older frame never follows a newer one
            if (open) last.get().ifPresent(d -> imageLocked(camera, d));
        }
        checkBacklog();
    }

    void image(String camera, ImageData d) {
        synchronized (lock) {
            if (!open || !camera.equals(selected)) return;
            imageLocked(camera, d);
        }
        checkBacklog();
    }

    private void imageLocked(String camera, ImageData d) {
        if (!sendable(d) || lastSent.get(camera) == d) return;
        if (imageInFlight) {
            pendingCamera = camera;
            pendingImage = d;
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
            String camera = pendingCamera;
            ImageData next = pendingImage;
            pendingCamera = null;
            pendingImage = null;
            if (next == null || !open || !camera.equals(selected) || lastSent.get(camera) == next) {
                imageInFlight = false;
                return;
            }
            write(camera, next);
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
