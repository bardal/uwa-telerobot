package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import au.edu.uwa.mech.sim.Block;
import au.edu.uwa.mech.web.archive.Archive;
import au.edu.uwa.mech.web.archive.FileArchiveStore;
import au.edu.uwa.mech.web.archive.Keys;
import au.edu.uwa.mech.web.cloud.MemoryParamStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The bridge against the real 2000 lab in this JVM ({@link LabFixture}): sessions on fake sockets
 * that record every frame the gateway sends.
 */
@Tag("it")
class BridgeIT {
    static final String PICK_AND_PLACE = "x120,y150,z60,s-90,t0,g0;z15;g1;z80;x330,y120;z45;g0;z90";
    /** Ten waypoints of at least 0.3 s each: still running when the socket closes. */
    static final String LONG_MOVE = "x200,y250,z120;x300,y250,z120;x200,y250,z120;x300,y250,z120;x200,y250,z120;"
        + "x300,y250,z120;x200,y250,z120;x300,y250,z120;x200,y250,z120;x300,y250,z120";

    static LabFixture lab;
    static SessionRegistry registry;
    static Path archiveDir;
    static Archive archive;

    @BeforeAll
    static void startLab() throws Exception {
        lab = LabFixture.shared();
        GatewayConfig cfg = new GatewayConfig(0, "127.0.0.1", lab.momPort(), lab.adminPort(),
            List.of("http://localhost"), null, true, null, null, null, List.of(), null, lab.gatewayPins(), null,
            false, "test", null, null, null);
        Auth auth = new Auth(cfg, (token, ip) -> true, null);
        archiveDir = Files.createTempDirectory("telerobot-archive");
        archive = new Archive(new FileArchiveStore(archiveDir), System::currentTimeMillis, 5L << 30);
        registry = new SessionRegistry(cfg, auth, new Bans(new MemoryParamStore()),
            new Idle(System::currentTimeMillis, () -> {}), System::currentTimeMillis, archive);
    }

    @AfterAll
    static void stop() {
        if (registry != null) registry.shutdown();
    }

    @Test
    void sessionsControlCommandsCamerasChatAndAdmin() throws Exception {
        // 1. welcome, then users with ann, then the controller
        FakeConnection a = connect(guest("ann"));
        Out.Welcome wa = a.await(Out.Welcome.class, w -> true, 20_000);
        assertEquals("ann", wa.nick());
        assertEquals("guest", wa.level());
        assertEquals(List.of("Colour", "YCam", "ZCam"), wa.cameras());
        int welcomeAt = a.indexOf(wa);
        Out.Users ua = a.await(Out.Users.class, u -> u.nicks().contains("ann"), 5_000);
        int usersAt = a.indexOf(ua);
        assertTrue(usersAt > welcomeAt, "users after welcome");
        Out.Controller ca = a.awaitAfter(usersAt, Out.Controller.class, c -> true, 5_000);
        assertNotNull(ca);
        assertTrue(a.frames().stream().anyMatch(f -> f instanceof Out.Lab l && l.state().equals("ready")), "lab ready");

        // 2. Colour: an image header with 12 calibration numbers, then the JPEG
        a.in(new In.SelectCamera("Colour"));
        Out.Image img = a.await(Out.Image.class, i -> i.camera().equals("Colour"), 10_000);
        assertEquals(12, img.calibration().size());
        assertTrue(img.width() > 0 && img.height() > 0);
        Object next = a.frameAfter(img, 5_000);
        assertTrue(next instanceof byte[], "binary frame follows the image header, got " + next);
        byte[] jpeg = (byte[]) next;
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xD8}, new byte[] {jpeg[0], jpeg[1]});
        assertTrue(a.frames().stream().noneMatch(f -> f instanceof Out.Image i && !i.camera().equals("Colour")),
            "only the selected camera is forwarded");

        // 3. take control and pick-and-place
        a.in(new In.TakeControl());
        a.await(Out.Controller.class, c -> "ann".equals(c.nick()), 10_000);
        a.in(new In.Command(PICK_AND_PLACE));
        Out.CommandStatus queued = a.await(Out.CommandStatus.class, s -> s.state().equals("queued"), 5_000);
        assertEquals(PICK_AND_PLACE, queued.script());
        Out.CommandStatus end = a.await(Out.CommandStatus.class,
            s -> s.id().equals(queued.id()) && (s.state().equals("done") || s.state().equals("error")), 60_000);
        assertEquals("done", end.state(), "pick-and-place: " + end.text());
        List<String> states = a.frames().stream()
            .filter(f -> f instanceof Out.CommandStatus s && s.id().equals(queued.id()))
            .map(f -> ((Out.CommandStatus) f).state()).toList();
        assertEquals("done", states.get(states.size() - 1), "statuses end in done: " + states);
        a.await(Out.RobotState.class, s -> near(s, 330, 120, 90) && !s.moving(), 10_000);
        Thread.sleep(500);
        Out.RobotState last = a.last(Out.RobotState.class);
        assertTrue(near(last, 330, 120, 90), "last robotState " + last);
        assertFalse(a.frames().stream().anyMatch(f -> f instanceof Out.Console c
            && (c.text().startsWith("Sending command") || c.text().contains("completed successfully"))),
            "Client's own command lines are not forwarded");

        // 4. a second ann
        FakeConnection b = connect(guest("ann"));
        assertEquals("ann~2", b.await(Out.Welcome.class, w -> true, 20_000).nick());
        a.await(Out.Users.class, u -> u.nicks().contains("ann~2"), 5_000);

        // 5. not in control
        b.in(new In.Command("x200,y200,z100"));
        assertEquals("not-in-control", b.await(Out.Error.class, e -> true, 5_000).code());

        // 6. chat
        b.in(new In.Chat("hi"));
        Out.Chat chat = a.await(Out.Chat.class, c -> c.nick().equals("ann~2"), 5_000);
        assertEquals("hi", chat.text());
        assertTrue(chat.time() > 0 && !chat.id().isEmpty());

        // 6b. the archive: after the pick-and-place and a flush, the move with 6 JPEGs, and chat "hi"
        Out.Archived moved = a.await(Out.Archived.class, x -> x.kind().equals("move"), 15_000);
        archive.flush();
        String moveId = moved.id();
        List<Path> moveImages = files("public/moves").stream()
            .filter(p -> p.getFileName().toString().startsWith(moveId + "-")).toList();
        assertEquals(6, moveImages.size(), "before and after for 3 cameras: " + moveImages);
        for (Path p : moveImages) {
            byte[] jpg = Files.readAllBytes(p);
            assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xD8}, new byte[] {jpg[0], jpg[1]}, p.toString());
        }
        JsonNode day = dayIndex(moveId);
        JsonNode move = entry(day, "move", moveId);
        assertEquals("done", move.get("result").asText());
        assertEquals(PICK_AND_PLACE, move.get("script").asText());
        assertEquals("ann", move.get("nick").asText());
        assertEquals(330, move.get("poseAfter").get("x").asDouble(), 1.5);
        for (String cam : List.of("colour", "ycam", "zcam")) {
            JsonNode f = move.get("frames").get(cam);
            assertNotNull(f, cam + " in " + move);
            assertTrue(f.get("before").asText().endsWith("-before.jpg") && f.get("after").asText().endsWith("-after.jpg"));
            assertEquals(12, f.get("calibration").size());
        }
        boolean hi = false;
        for (JsonNode e : day.get("entries")) {
            hi |= e.get("kind").asText().equals("chat") && e.get("text").asText().equals("hi")
                && e.get("nick").asText().equals("ann~2");
        }
        assertTrue(hi, "chat hi in " + day);
        assertTrue(Files.readString(archiveDir.resolve(Keys.DAYS)).contains(Keys.day(Keys.idTime(moveId))));

        // 6c. Archive Images: a snapshot of 3 images, announced with archived
        b.in(new In.ArchiveImages());
        Out.Archived snap = b.await(Out.Archived.class, x -> x.kind().equals("snapshot"), 10_000);
        assertTrue(snap.url().startsWith("/archive/public/"), snap.url());
        archive.flush();
        assertEquals(3, files("public/snapshots").stream()
            .filter(p -> p.getFileName().toString().startsWith(snap.id() + "-")).count());
        assertNotNull(entry(dayIndex(snap.id()), "snapshot", snap.id()));

        // 7. registered C takes control from guest ann at once
        FakeConnection c = connect(new In.Hello("C", null, null, new In.Dev("c-sub", List.of())));
        assertEquals("registered", c.await(Out.Welcome.class, w -> true, 20_000).level());
        c.in(new In.TakeControl());
        a.await(Out.Controller.class, x -> "C".equals(x.nick()), 5_000);
        c.await(Out.Controller.class, x -> "C".equals(x.nick()), 5_000);

        // 8. C's tab closes mid-command: control is released and ann can take it straight away
        int beforeLong = a.size();
        c.in(new In.Command(LONG_MOVE));
        String longId = c.await(Out.CommandStatus.class, s -> s.state().equals("queued"), 5_000).id();
        c.await(Out.CommandStatus.class, s -> s.id().equals(longId) && s.state().equals("sent"), 5_000);
        a.awaitFrom(beforeLong, Out.RobotState.class, s -> s.moving(), 5_000);
        int beforeClose = a.size();
        long closedAt = System.currentTimeMillis();
        c.dropSocket();
        a.awaitFrom(beforeClose, Out.Controller.class, x -> x.nick() == null, 2_000);
        assertTrue(System.currentTimeMillis() - closedAt <= 2_000);
        int beforeTake = a.size();
        a.in(new In.TakeControl());
        a.awaitFrom(beforeTake, Out.Controller.class, x -> "ann".equals(x.nick()), 5_000);
        a.await(Out.Users.class, u -> !u.nicks().contains("C") && u.nicks().contains("ann"), 15_000);

        // 9. admin reset puts red back; a guest may not reset
        a.in(new In.Admin("reset", null, null));
        assertEquals("unauthorised", a.await(Out.Error.class, e -> e.code().equals("unauthorised"), 5_000).code());
        Block redBefore = lab.block("red").orElseThrow();
        assertFalse(Math.abs(redBefore.getX() - 120) < 1 && Math.abs(redBefore.getY() - 150) < 1,
            "red was moved by the pick-and-place");
        FakeConnection d = connect(new In.Hello("D", null, null, new In.Dev("d-sub", List.of("admin"))));
        assertEquals("admin", d.await(Out.Welcome.class, w -> true, 20_000).level());
        d.in(new In.Admin("reset", null, null));
        long until = System.currentTimeMillis() + 10_000;
        while (true) {
            Block red = lab.block("red").orElseThrow();
            if (Math.abs(red.getX() - 120) < 1 && Math.abs(red.getY() - 150) < 1) break;
            if (System.currentTimeMillis() > until) fail("red not back at (120,150): " + red.getX() + "," + red.getY());
            Thread.sleep(50);
        }

        // 9b. an admin deletes the snapshot: its images and its index entry go
        int beforeDelete = d.size();
        d.in(new In.Admin("deleteSnapshot", null, snap.id()));
        d.awaitFrom(beforeDelete, Out.Console.class, x -> x.text().contains(snap.id()), 10_000);
        assertEquals(0, files("public/snapshots").stream()
            .filter(p -> p.getFileName().toString().startsWith(snap.id() + "-")).count());
        assertEquals(null, entry(dayIndex(snap.id()), "snapshot", snap.id()));
        int beforeMissing = d.size();
        d.in(new In.Admin("deleteMove", null, "1760000000000-zzzz"));
        d.awaitFrom(beforeMissing, Out.Error.class, x -> true, 5_000);

        // 10. the robot's own error text comes back in commandStatus
        int beforeBad = a.size();
        a.in(new In.Command("x700,y200,z100"));
        Out.CommandStatus bad = a.awaitFrom(beforeBad, Out.CommandStatus.class,
            s -> s.state().equals("done") || s.state().equals("error"), 30_000);
        assertEquals("error", bad.state());
        assertTrue(bad.text().contains("outside the workspace"), bad.text());

        // 10b. a two-line script (the UI's textarea form) is one protocol line: it completes, and the next
        //      command's reply is not off by one
        int beforeLines = a.size();
        a.in(new In.Command("x200,y300,z60\nz40"));
        Out.CommandStatus multi = a.awaitFrom(beforeLines, Out.CommandStatus.class,
            s -> s.state().equals("done") || s.state().equals("error"), 30_000);
        assertEquals("done", multi.state(), "two-line script: " + multi.text());
        int beforeNext = a.size();
        a.in(new In.Command("z60"));
        Out.CommandStatus next2 = a.awaitFrom(beforeNext, Out.CommandStatus.class,
            s -> s.state().equals("done") || s.state().equals("error"), 30_000);
        assertEquals("done", next2.state(), "following command: " + next2.text());

        // 11. at most 4 commands queued
        int beforeQueue = a.size();
        for (String z : List.of("z100", "z110", "z100", "z110", "z100")) a.in(new In.Command(z));
        Out.Error full = a.awaitFrom(beforeQueue, Out.Error.class, e -> true, 5_000);
        assertEquals("rate-limited", full.code());
        assertTrue(full.message().contains("at most 4"), full.message());
        long until4 = System.currentTimeMillis() + 30_000;
        while (a.countFrom(beforeQueue, Out.CommandStatus.class, s -> s.state().equals("done")) < 4) {
            if (System.currentTimeMillis() > until4) fail("the 4 queued commands did not finish");
            Thread.sleep(50);
        }
        assertEquals(4, a.countFrom(beforeQueue, Out.CommandStatus.class, s -> s.state().equals("queued")));

        // 12. a kicked client that ignores the close: its commands stop at once and its control goes
        a.in(new In.Release());
        a.await(Out.Controller.class, x -> x.nick() == null, 5_000);
        FakeConnection s = connect(guest("sam"), "10.0.0.5", true);
        assertEquals("sam", s.await(Out.Welcome.class, w -> true, 20_000).nick());
        s.in(new In.TakeControl());
        s.await(Out.Controller.class, x -> "sam".equals(x.nick()), 5_000);
        int beforeKick = a.size();
        d.in(new In.Admin("kick", "SAM", null)); // nicks match ignoring case
        a.awaitFrom(beforeKick, Out.Controller.class, x -> x.nick() == null, 2_000);
        assertTrue(s.closedByServer);
        assertEquals("unauthorised", s.await(Out.Error.class, e -> true, 1_000).code());
        var poseBefore = lab.world().getPose();
        for (int i = 0; i < 3; i++) s.in(new In.Command("x150,y300,z60"));
        Thread.sleep(1_500);
        assertEquals(poseBefore, lab.world().getPose(), "a closed session's commands never reach the robot");
        assertFalse(lab.world().isMoving());
        a.await(Out.Users.class, u -> !u.nicks().contains("sam"), 15_000);
        s.dropSocket();

        // 13. takeControl and the tab closes at once: the next user is not blocked for 180 s
        FakeConnection e = connect(guest("eve"), "10.0.0.6", false);
        e.await(Out.Welcome.class, w -> true, 20_000);
        a.await(Out.Users.class, u -> u.nicks().contains("eve"), 5_000);
        int beforeDrop = a.size();
        e.in(new In.TakeControl());
        e.dropSocket();
        // Only a users list sent after the drop: the lists from before eve joined lack her too,
        // and taking control on one of those raced eve's MOM logout (her grab, then ann's grab
        // refused with "ann can't grab token from eve", then her release).
        a.awaitFrom(beforeDrop, Out.Users.class, u -> !u.nicks().contains("eve"), 15_000);
        int beforeAnn = a.size();
        a.in(new In.TakeControl());
        // 10 s of headroom on a loaded build machine; still far below MOM's 180 s hold, which is
        // what this step guards against (the flake itself was the users-list wait above)
        a.awaitFrom(beforeAnn, Out.Controller.class, x -> "ann".equals(x.nick()), 10_000);

        // 14. ban: the session goes, and the same guest can't come back
        FakeConnection f1 = connect(guest("fay"), "10.0.0.7", false);
        f1.await(Out.Welcome.class, w -> true, 20_000);
        d.in(new In.Admin("ban", "fay", null));
        assertEquals("unauthorised", f1.await(Out.Error.class, x -> true, 5_000).code());
        FakeConnection f2 = connect(guest("fay"), "10.0.0.7", false);
        assertEquals("unauthorised", f2.await(Out.Error.class, x -> true, 10_000).code());
        assertTrue(f2.frames().stream().noneMatch(x -> x instanceof Out.Welcome));
        assertTrue(a.isOpen() && b.isOpen(), "other addresses are not banned");

        for (FakeConnection f : List.of(a, b, d)) f.dropSocket();
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** Every file under the archive's {@code prefix} directory. */
    static List<Path> files(String prefix) throws Exception {
        Path root = archiveDir.resolve(prefix);
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    /** The stored day index holding the entry with this id. */
    static JsonNode dayIndex(String id) throws Exception {
        Path p = archiveDir.resolve(Keys.dayIndex(Keys.day(Keys.idTime(id))));
        return Protocol.mapper().readTree(Files.readString(p));
    }

    static JsonNode entry(JsonNode day, String kind, String id) {
        for (JsonNode e : day.get("entries")) {
            if (e.get("kind").asText().equals(kind) && e.get("id").asText().equals(id)) return e;
        }
        return null;
    }

    static In.Hello guest(String nick) {
        return new In.Hello(nick, "turnstile-ok", null, null);
    }

    static boolean near(Out.RobotState s, double x, double y, double z) {
        return Math.abs(s.x() - x) < 1.5 && Math.abs(s.y() - y) < 1.5 && Math.abs(s.z() - z) < 1.5;
    }

    static FakeConnection connect(In.Hello hello) {
        return connect(hello, "127.0.0.1", false);
    }

    /** {@code stubborn}: a client that ignores our close (its socket stays up until it drops it). */
    static FakeConnection connect(In.Hello hello, String ip, boolean stubborn) {
        FakeConnection f = new FakeConnection(registry, ip, stubborn);

        registry.onHello(f, hello);
        return f;
    }

    /** A browser socket that keeps every frame. Like GatewayServer, it reports its end once. */
    static final class FakeConnection implements ClientConnection {
        private final SessionHandler handler;
        private final List<Object> frames = new ArrayList<>();
        private final AtomicBoolean ended = new AtomicBoolean();
        private volatile boolean open = true;
        private volatile Object attachment;
        private final String ip;
        private final boolean stubborn;
        volatile boolean closedByServer;

        FakeConnection(SessionHandler handler, String ip, boolean stubborn) {
            this.handler = handler;
            this.ip = ip;
            this.stubborn = stubborn;
        }

        void in(In message) {
            handler.onMessage(this, message);
        }

        /** The browser goes away (tab closed): the server calls onClose. */
        void dropSocket() {
            end();
        }

        private void end() {
            if (!ended.compareAndSet(false, true)) return;
            open = false;
            Thread t = new Thread(() -> handler.onClose(this), "fake-onclose");
            t.start();
        }

        @Override public String remoteIp() { return ip; }

        /** Frames are "written" at once. */
        @Override
        public void send(Out message, Runnable written) {
            add(message);
            written.run();
        }

        @Override
        public void sendImage(Out.Image header, byte[] jpeg, Runnable written) {
            synchronized (frames) {
                add(header);
                add(jpeg.clone());
            }
            written.run();
        }

        @Override public long queuedBytes() { return 0; }
        @Override public int queuedMessages() { return 0; }

        private void add(Object frame) {
            synchronized (frames) {
                if (!open) return;
                frames.add(frame);
                frames.notifyAll();
            }
        }

        @Override
        public void close(int code, String reason) {
            closedByServer = true;
            if (!stubborn) end();
        }

        @Override public boolean isOpen() { return open; }
        @Override public Object attachment() { return attachment; }
        @Override public void attach(Object state) { attachment = state; }

        List<Object> frames() {
            synchronized (frames) {
                return new ArrayList<>(frames);
            }
        }

        int size() {
            synchronized (frames) {
                return frames.size();
            }
        }

        int indexOf(Object frame) {
            synchronized (frames) {
                for (int i = 0; i < frames.size(); i++) if (frames.get(i) == frame) return i;
                return -1;
            }
        }

        <T> long countFrom(int from, Class<T> type, Predicate<T> p) {
            List<Object> all = frames();
            return all.subList(Math.min(from, all.size()), all.size()).stream()
                .filter(type::isInstance).map(type::cast).filter(p).count();
        }

        <T> T last(Class<T> type) {
            List<Object> all = frames();
            for (int i = all.size() - 1; i >= 0; i--) if (type.isInstance(all.get(i))) return type.cast(all.get(i));
            return null;
        }

        <T> T await(Class<T> type, Predicate<T> p, long millis) throws InterruptedException {
            return awaitAfter(-1, type, p, millis);
        }

        /** Like awaitAfter, from index {@code from} (a size() taken earlier) on. */
        <T> T awaitFrom(int from, Class<T> type, Predicate<T> p, long millis) throws InterruptedException {
            return awaitAfter(from - 1, type, p, millis);
        }

        /** The first frame of the type matching p with an index greater than after. */
        <T> T awaitAfter(int after, Class<T> type, Predicate<T> p, long millis) throws InterruptedException {
            long end = System.currentTimeMillis() + millis;
            synchronized (frames) {
                int i = after + 1;
                while (true) {
                    for (; i < frames.size(); i++) {
                        Object f = frames.get(i);
                        if (type.isInstance(f) && p.test(type.cast(f))) return type.cast(f);
                    }
                    long left = end - System.currentTimeMillis();
                    if (left <= 0) {
                        fail("timed out waiting for " + type.getSimpleName() + "; got " + describe(frames));
                    }
                    frames.wait(left);
                }
            }
        }

        Object frameAfter(Object frame, long millis) throws InterruptedException {
            long end = System.currentTimeMillis() + millis;
            synchronized (frames) {
                while (true) {
                    int i = indexOf(frame);
                    if (i >= 0 && i + 1 < frames.size()) return frames.get(i + 1);
                    long left = end - System.currentTimeMillis();
                    if (left <= 0) return null;
                    frames.wait(left);
                }
            }
        }

        private static String describe(List<Object> frames) {
            List<String> out = new ArrayList<>();
            int from = Math.max(0, frames.size() - 40);
            for (Object f : frames.subList(from, frames.size())) {
                out.add(f instanceof byte[] b ? "binary(" + b.length + ")" : Objects.toString(f));
            }
            return out.toString();
        }
    }
}
