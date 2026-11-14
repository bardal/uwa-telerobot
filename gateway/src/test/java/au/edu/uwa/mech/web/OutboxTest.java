package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import au.edu.uwa.mech.devices.camera.ImageData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The outgoing side of a session against a socket whose writes we finish by hand. */
class OutboxTest {

    /** Every frame stays "queued" until {@link #complete()} finishes the oldest write. */
    static class ManualConnection implements ClientConnection {
        final List<Object> sent = new ArrayList<>();
        final ArrayDeque<Runnable> writes = new ArrayDeque<>();
        long bytes;

        @Override public String remoteIp() { return "127.0.0.1"; }

        @Override
        public void send(Out message, Runnable written) {
            sent.add(message);
            bytes += 100;
            writes.add(() -> { bytes -= 100; written.run(); });
        }

        @Override
        public void sendImage(Out.Image header, byte[] jpeg, Runnable written) {
            sent.add(header);
            sent.add(jpeg);
            bytes += jpeg.length;
            writes.add(() -> { bytes -= jpeg.length; written.run(); });
        }

        void complete() {
            writes.poll().run();
        }

        void completeAll() {
            while (!writes.isEmpty()) complete();
        }

        @Override public long queuedBytes() { return bytes; }
        @Override public int queuedMessages() { return writes.size(); }
        @Override public void close(int code, String reason) { }
        @Override public boolean isOpen() { return true; }
        @Override public Object attachment() { return null; }
        @Override public void attach(Object state) { }

        long count(Class<?> type) {
            return sent.stream().filter(type::isInstance).count();
        }
    }

    final ManualConnection conn = new ManualConnection();
    final AtomicInteger overflows = new AtomicInteger();
    final Outbox box = new Outbox(conn, overflows::incrementAndGet);

    static ImageData frame(int size) {
        ImageData d = new ImageData();
        d.width = 400;
        d.height = 300;
        d.pixels = new byte[size];
        d.pixels[0] = (byte) 0xFF;
        d.pixels[1] = (byte) 0xD8;
        return d;
    }

    static Out.RobotState state(double x) {
        return new Out.RobotState(x, 0, 0, 0, 0, true, true);
    }

    void open() {
        box.open(List.of(new Out.Lab("ready", "")), List::of);
        conn.completeAll();
        conn.sent.clear();
    }

    @Test
    void aSlowBrowserGetsOnlyTheNewestFrameWhileOneIsInFlight() {
        open();
        box.select("Colour", Optional::empty);
        ImageData d1 = frame(1000), d2 = frame(1000), d3 = frame(1000);
        box.image("Colour", d1);
        box.image("Colour", d2);
        box.image("Colour", d3);
        assertEquals(2, conn.sent.size(), "one header and one JPEG in flight");
        assertSame(d1.pixels, conn.sent.get(1));
        conn.complete();
        assertEquals(4, conn.sent.size());
        assertSame(d3.pixels, conn.sent.get(3), "d2 was dropped for the newer d3");
        Out.Image h = (Out.Image) conn.sent.get(2);
        assertEquals(2, h.seq());
        assertEquals(12, h.calibration().size());
        conn.complete();
        assertEquals(4, conn.sent.size(), "nothing else pending");
        box.image("Colour", frame(1000));
        assertEquals(6, conn.sent.size(), "idle again: the next frame goes at once");
    }

    @Test
    void onlyTheSelectedCameraAndNoFrameWithoutCalibration() {
        open();
        box.image("Colour", frame(10));
        assertEquals(0, conn.sent.size(), "nothing selected yet");
        box.select("YCam", Optional::empty);
        box.image("Colour", frame(10));
        ImageData broken = frame(10);
        broken.calibrationMatrix = null;
        box.image("YCam", broken);
        assertEquals(0, conn.sent.size());
        box.image("YCam", frame(10));
        assertEquals(2, conn.sent.size());
    }

    @Test
    void selectingSendsTheLastPhotoOnceAndDropsTheOldCamerasPendingFrame() {
        open();
        ImageData last = frame(10);
        box.select("Colour", () -> Optional.of(last));
        assertEquals(2, conn.sent.size());
        box.image("Colour", last); // the live copy of the same frame
        box.image("Colour", frame(10)); // pending
        box.select("ZCam", Optional::empty);
        conn.completeAll();
        assertEquals(2, conn.sent.size(), "the Colour frame pending at the switch is dropped");
    }

    @Test
    void everyWatchedCameraGetsItsOwnNewestFrameWhileOneIsInFlight() {
        open();
        box.watch(List.of("Colour", "YCam", "ZCam"), c -> Optional.empty());
        ImageData c1 = frame(10), y1 = frame(10), z1 = frame(10), y2 = frame(10);
        box.image("Colour", c1); // in flight
        box.image("YCam", y1);
        box.image("ZCam", z1);
        box.image("YCam", y2); // replaces y1; ZCam's frame must survive it
        assertEquals(2, conn.sent.size(), "one header and one JPEG in flight");
        conn.complete();
        assertEquals("YCam", ((Out.Image) conn.sent.get(2)).camera(), "the camera that has waited longest goes next");
        assertSame(y2.pixels, conn.sent.get(3), "y1 was dropped for the newer y2");
        conn.complete();
        assertEquals("ZCam", ((Out.Image) conn.sent.get(4)).camera());
        assertSame(z1.pixels, conn.sent.get(5));
        conn.complete();
        assertEquals(6, conn.sent.size(), "nothing else pending");
        box.image("ZCam", frame(10));
        assertEquals(8, conn.sent.size(), "idle again: the next frame goes at once");
    }

    @Test
    void watchingStartsEachCameraWithItsLastPhotoAndStopsTheOthers() {
        open();
        ImageData colour = frame(10), ycam = frame(10);
        java.util.Map<String, ImageData> last = new java.util.HashMap<>(java.util.Map.of("Colour", colour, "YCam", ycam));
        box.watch(List.of("Colour", "YCam"), c -> Optional.ofNullable(last.get(c)));
        conn.completeAll();
        assertEquals(List.of("Colour", "YCam"),
            conn.sent.stream().filter(Out.Image.class::isInstance).map(m -> ((Out.Image) m).camera()).toList());
        assertTrue(box.watches("YCam") && !box.watches("ZCam"));
        assertEquals(java.util.Set.of("Colour", "YCam"), box.watching());
        conn.sent.clear();

        box.image("ZCam", frame(10));
        assertEquals(0, conn.sent.size(), "ZCam is not on show");
        ImageData colour2 = frame(10);
        last.put("Colour", colour2); // a camera's last photo is the frame the live path delivers
        box.image("Colour", colour2); // in flight
        box.image("YCam", frame(10)); // pending
        box.watch(List.of("Colour", "ZCam"), c -> Optional.ofNullable(last.get(c)));
        conn.completeAll();
        assertEquals(2, conn.sent.size(), "YCam's pending frame is dropped, and Colour's last photo is not sent twice");
    }
    @Test
    void robotStatesCoalesceToTheLatest() {
        open();
        box.robotState(state(1));
        box.robotState(state(2));
        box.robotState(state(3));
        assertEquals(List.of(state(1)), conn.sent);
        conn.complete();
        assertEquals(List.of(state(1), state(3)), conn.sent);
        conn.complete();
        box.robotState(state(4));
        assertEquals(state(4), conn.sent.get(2));
    }

    /**
     * The browser may answer the welcome at once (BridgeIT's fake client does, on another
     * thread): by the time the welcome is on the wire the session must already accept
     * messages, or that answer gets "wait for welcome". Live events still cannot overtake
     * the welcome, because emit() takes the same lock as open().
     */
    @Test
    void isOpenBeforeTheWelcomeIsSent() {
        List<Boolean> openWhenSent = new ArrayList<>();
        Outbox[] self = new Outbox[1];
        ManualConnection watching = new ManualConnection() {
            @Override
            public void send(Out message, Runnable written) {
                openWhenSent.add(self[0].isOpen());
                super.send(message, written);
            }
        };
        self[0] = new Outbox(watching, () -> { });
        self[0].open(List.of(new Out.Console("welcome stand-in"), new Out.Lab("ready", "")),
            () -> List.of(new Out.Users(List.of())));
        assertEquals(List.of(true, true, true), openWhenSent);
        assertEquals(List.of("console", "lab", "users"),
            watching.sent.stream().map(m -> ((Out) m).t()).toList(), "the first messages, then the snapshot");
    }

    @Test
    void nothingLiveBeforeOpenOrAfterClose() {
        box.emit(new Out.Console("early"));
        box.robotState(state(1));
        box.sendNow(new Out.Error("bad-message", "x"));
        assertEquals(1, conn.sent.size());
        open();
        box.close();
        box.emit(new Out.Console("late"));
        box.image("Colour", frame(10));
        assertEquals(0, conn.sent.size());
    }

    @Test
    void aBacklogPastTheLimitClosesOnce() {
        open();
        for (int i = 0; i <= Outbox.MAX_QUEUED_MESSAGES; i++) box.emit(new Out.Console("line " + i));
        assertEquals(1, overflows.get());
        box.emit(new Out.Console("more"));
        assertEquals(1, overflows.get(), "once only");
    }

    @Test
    void aBacklogOfBytesClosesToo() {
        open();
        box.select("Colour", Optional::empty);
        box.image("Colour", frame((int) Outbox.MAX_QUEUED_BYTES + 1));
        assertEquals(1, overflows.get());
        assertTrue(conn.count(Out.Image.class) == 1);
    }
}
