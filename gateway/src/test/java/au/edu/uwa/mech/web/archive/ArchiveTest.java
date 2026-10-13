package au.edu.uwa.mech.web.archive;

import static org.junit.jupiter.api.Assertions.*;

import au.edu.uwa.mech.devices.camera.ImageData;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.web.Identity;
import au.edu.uwa.mech.web.Identity.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final long OCT_13 = 1_791_849_600_000L + 10 * 3_600_000L; // 2026-10-13T10:00Z
    static final long HOUR = 3_600_000L;

    @TempDir Path dir;
    long now = OCT_13;
    FileArchiveStore store;
    Archive archive;
    final Identity ann = new Identity("sub-ann-secret", Level.GUEST, "iphash-ann-secret");

    @BeforeEach
    void setUp() {
        store = new FileArchiveStore(dir);
        archive = new Archive(store, () -> now, 5_000_000_000L, new Random(42));
    }

    static ImageData img(int n) {
        ImageData d = new ImageData();
        d.pixels = new byte[n];
        d.calibrationMatrix.set(0, 0, 1.5);
        d.calibrationMatrix.set(2, 3, 9);
        return d;
    }

    static Map<String, ImageData> three(int n) {
        Map<String, ImageData> m = new LinkedHashMap<>();
        m.put("Colour", img(n));
        m.put("Left", img(n));
        m.put("Right", img(n));
        return m;
    }

    List<String> files() throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).map(p -> dir.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
    }

    long count(String suffix) throws IOException {
        return files().stream().filter(f -> f.endsWith(suffix)).count();
    }

    JsonNode readJson(String key) throws IOException {
        return JSON.readTree(store.get(key).orElseThrow());
    }

    @Test
    void moveWritesSixImagesAndAnIndexEntry() throws IOException {
        String id = archive.startMove("ann", Level.GUEST, ann, "move 1 2 3", three(10), new RobotState());
        now += 5000;
        archive.finishMove(id, "done", new RobotState(), three(11));
        archive.flush();

        assertEquals(6, count(".jpg"));
        assertTrue(id.matches("^" + OCT_13 + "-[a-z0-9]{4}$"), id);
        JsonNode e = readJson("public/index/2026-10-13.json").get("entries").get(0);
        assertEquals("move", e.get("kind").asText());
        assertEquals(id, e.get("id").asText());
        assertEquals(OCT_13, e.get("time").asLong());
        assertEquals("ann", e.get("nick").asText());
        assertEquals("guest", e.get("level").asText());
        assertEquals("move 1 2 3", e.get("script").asText());
        assertEquals("done", e.get("result").asText());
        assertTrue(e.get("poseBefore").get("gripperOpen").isBoolean());
        assertTrue(e.get("poseAfter").has("spin"));
        assertTrue(e.get("poseAfter").has("tilt"));
        JsonNode colour = e.get("frames").get("colour");
        assertTrue(colour.get("before").asText().startsWith("public/moves/2026/10/13/" + id + "-colour-before"));
        assertTrue(colour.get("after").asText().endsWith("-colour-after.jpg"));
        assertEquals(12, colour.get("calibration").size());
        assertEquals(1.5, colour.get("calibration").get(0).asDouble());
        assertEquals(9.0, colour.get("calibration").get(11).asDouble());
        assertTrue(e.get("frames").has("left"));
        assertTrue(e.get("frames").has("right"));
        for (String f : files()) assertTrue(f.matches("^(public/(moves|snapshots|index)/|private/audit/).*"), f);
    }

    @Test
    void snapshotWritesThreeImagesAndEleventhInAnHourIsRefused() throws IOException {
        var first = archive.snapshot("ann", ann, three(10), new RobotState());
        assertTrue(first.isPresent());
        assertEquals(3, count(".jpg"));
        for (int i = 1; i < 10; i++) {
            now += 1000;
            assertTrue(archive.snapshot("ann", ann, three(10), new RobotState()).isPresent(), "snapshot " + (i + 1));
        }
        now += 1000;
        assertTrue(archive.snapshot("ann", ann, three(10), new RobotState()).isEmpty());
        assertEquals(30, count(".jpg"));
        // another user is unaffected
        assertTrue(archive.snapshot("bob", new Identity("bob-sub", Level.GUEST, "h"), three(10), new RobotState()).isPresent());
        // an hour after the first, the window has moved on
        now = OCT_13 + HOUR + 1;
        assertTrue(archive.snapshot("ann", ann, three(10), new RobotState()).isPresent());

        archive.flush();
        JsonNode e = readJson("public/index/2026-10-13.json").get("entries").get(0);
        assertEquals("snapshot", e.get("kind").asText());
        assertTrue(e.get("frames").get("colour").asText().endsWith("-colour.jpg"));
        assertTrue(e.has("pose"));
    }

    @Test
    void chatAppearsInTimeOrder() throws IOException {
        archive.chat("ann", ann, "first");
        now += 10;
        String mv = archive.startMove("ann", Level.GUEST, ann, "go", Map.of(), new RobotState());
        now += 10;
        archive.chat("bob", new Identity("b", Level.REGISTERED, "h"), "second");
        archive.finishMove(mv, "done", new RobotState(), Map.of());
        archive.flush();
        JsonNode entries = readJson("public/index/2026-10-13.json").get("entries");
        assertEquals(3, entries.size());
        assertEquals("first", entries.get(0).get("text").asText());
        assertEquals("move", entries.get(1).get("kind").asText());
        assertEquals("second", entries.get(2).get("text").asText());
        assertEquals("chat", entries.get(2).get("kind").asText());
    }

    @Test
    void atMost5000ChatsADayAreArchivedThenTheNextDayStartsAfresh() throws IOException {
        for (int i = 0; i < Archive.CHATS_PER_DAY; i++) {
            assertNotNull(archive.chat("ann", ann, "c" + i), "chat " + i);
            now += 1;
        }
        assertNull(archive.chat("ann", ann, "one too many"), "the 5001st is not archived");
        assertNull(archive.chat("ann", ann, "nor the next"));
        // moves and snapshots are not chat: they are still archived
        assertTrue(archive.snapshot("ann", ann, Map.of(), new RobotState()).isPresent());
        archive.flush();
        JsonNode entries = readJson("public/index/2026-10-13.json").get("entries");
        long chats = 0;
        for (JsonNode e : entries) if ("chat".equals(e.get("kind").asText())) chats++;
        assertEquals(Archive.CHATS_PER_DAY, chats);

        // a restarted gateway counts the day's stored chat
        Archive again = new Archive(store, () -> now, 5_000_000_000L, new Random(7));
        assertNull(again.chat("ann", ann, "after a restart"), "the stored 5000 count toward the cap");

        now = OCT_13 + 24 * HOUR; // the next UTC day
        assertNotNull(archive.chat("ann", ann, "a new day"));
        assertNotNull(again.chat("ann", ann, "a new day, too"));
    }

    @Test
    void hostileNickAndScriptLeaveNoTraceInKeys() throws IOException {
        String id = archive.startMove("../../x", Level.GUEST, ann, "a/b/../c", three(5), new RobotState());
        archive.finishMove(id, "done", new RobotState(), three(5));
        archive.snapshot("../../x", ann, three(5), new RobotState());
        archive.chat("../../x", ann, "hi/../there");
        archive.flush();
        assertFalse(files().isEmpty());
        for (String f : files()) {
            assertTrue(f.matches("^(public/(moves|snapshots|index)/|private/audit/)[a-z0-9/_.-]+$"), f);
            assertFalse(f.contains(".."), f);
        }
        // the nick is data, so it round-trips through JSON rather than leaking into a key
        assertEquals("../../x", readJson("public/index/2026-10-13.json").get("entries").get(0).get("nick").asText());
    }

    @Test
    void cameraNamesAreFilteredAndMissingCamerasTolerated() throws IOException {
        Map<String, ImageData> frames = new LinkedHashMap<>();
        frames.put("Co/../Lour 1", img(5));
        ImageData noCal = img(5);
        noCal.calibrationMatrix = null;
        frames.put("Nocal", noCal);
        frames.put("Nopix", new ImageData());
        frames.put("Nullcam", null);
        String id = archive.startMove("ann", Level.GUEST, ann, "x", frames, null);
        archive.finishMove(id, "done", null, null);
        archive.flush();
        JsonNode e = readJson("public/index/2026-10-13.json").get("entries").get(0);
        assertTrue(e.get("frames").has("colour1"));
        assertFalse(e.get("frames").has("nocal"));
        assertFalse(e.get("frames").has("nopix"));
        assertEquals(1, count(".jpg"));
    }

    @Test
    void overTheCapImagesAreSkippedButChatAndMoveEntriesStay() throws IOException {
        archive = new Archive(store, () -> now, 100, new Random(1));
        String id = archive.startMove("ann", Level.GUEST, ann, "x", three(1000), new RobotState());
        archive.finishMove(id, "done", new RobotState(), three(1000));
        archive.chat("ann", ann, "still here");
        archive.flush();
        assertEquals(0, count(".jpg"));
        JsonNode entries = readJson("public/index/2026-10-13.json").get("entries");
        JsonNode move = null;
        boolean chat = false;
        for (JsonNode n : entries) {
            if (n.get("kind").asText().equals("move")) move = n;
            if (n.get("kind").asText().equals("chat")) chat = true;
        }
        assertNotNull(move);
        assertTrue(chat);
        assertEquals("done", move.get("result").asText());
    }

    @Test
    void snapshotIsRefusedWhenCapped() {
        Archive capped = new Archive(store, () -> now, 0, new Random(1));
        assertTrue(capped.snapshot("ann", ann, three(10), new RobotState()).isEmpty());
    }

    @Test
    void deleteRemovesImagesAndIndexEntry() throws IOException {
        String id = archive.startMove("ann", Level.GUEST, ann, "x", three(10), new RobotState());
        archive.finishMove(id, "done", new RobotState(), three(10));
        String keep = archive.chat("ann", ann, "keep me");
        archive.flush();
        assertEquals(6, count(".jpg"));

        assertTrue(archive.delete("move", id));
        assertEquals(0, count(".jpg"));
        JsonNode entries = readJson("public/index/2026-10-13.json").get("entries");
        assertEquals(1, entries.size());
        assertEquals(keep, entries.get(0).get("id").asText());
        assertFalse(archive.delete("move", id));
        assertFalse(archive.delete("move", "nonsense"));
        assertFalse(archive.delete("../x", id));
    }

    @Test
    void deleteWorksAfterRestartAndForSnapshotsAndChat() throws IOException {
        var snap = archive.snapshot("ann", ann, three(10), new RobotState()).orElseThrow();
        String chat = archive.chat("ann", ann, "bye");
        archive.flush();
        Archive again = new Archive(store, () -> now, 5_000_000_000L, new Random(7));
        assertTrue(again.delete("snapshot", snap));
        assertTrue(again.delete("chat", chat));
        assertEquals(0, count(".jpg"));
        assertEquals(0, readJson("public/index/2026-10-13.json").get("entries").size());
    }

    @Test
    void auditHoldsSubAndIpHashButPublicIndexDoesNot() throws IOException {
        String id = archive.startMove("ann", Level.GUEST, ann, "x", three(10), new RobotState());
        archive.finishMove(id, "done", new RobotState(), three(10));
        archive.snapshot("ann", ann, three(10), new RobotState());
        archive.chat("ann", ann, "hello");
        archive.flush();

        String audit = new String(store.get("private/audit/2026-10-13.jsonl").orElseThrow(), StandardCharsets.UTF_8);
        assertTrue(audit.contains("sub-ann-secret"));
        assertTrue(audit.contains("iphash-ann-secret"));
        assertEquals(3, audit.strip().split("\n").length);
        for (String line : audit.strip().split("\n")) JSON.readTree(line);

        for (String f : files()) {
            if (f.startsWith("public/") && f.endsWith(".json")) {
                String body = new String(Files.readAllBytes(dir.resolve(f)), StandardCharsets.UTF_8);
                assertFalse(body.contains("sub-ann-secret"), f);
                assertFalse(body.contains("iphash-ann-secret"), f);
            }
        }

        // a second flush appends only new lines
        archive.chat("ann", ann, "again");
        archive.flush();
        archive.flush();
        audit = new String(store.get("private/audit/2026-10-13.jsonl").orElseThrow(), StandardCharsets.UTF_8);
        assertEquals(4, audit.strip().split("\n").length);
    }

    @Test
    void daysJsonIsNewestFirst() throws IOException {
        archive.chat("ann", ann, "d1");
        now += 24 * HOUR;
        archive.chat("ann", ann, "d2");
        now -= 3 * 24 * HOUR;
        archive.chat("ann", ann, "d0");
        archive.flush();
        JsonNode days = readJson("public/index/days.json").get("days");
        assertEquals(List.of("2026-10-14", "2026-10-13", "2026-10-11"), texts(days));

        // survives a restart and keeps merging
        Archive again = new Archive(store, () -> now + 10 * 24 * HOUR, 5_000_000_000L, new Random(3));
        again.chat("ann", ann, "later");
        again.flush();
        assertEquals(List.of("2026-10-21", "2026-10-14", "2026-10-13", "2026-10-11"),
                texts(readJson("public/index/days.json").get("days")));
    }

    @Test
    void restartKeepsEarlierEntriesInTheSameDay() throws IOException {
        archive.chat("ann", ann, "one");
        archive.flush();
        Archive again = new Archive(store, () -> now + 1, 5_000_000_000L, new Random(9));
        again.chat("ann", ann, "two");
        again.flush();
        assertEquals(2, readJson("public/index/2026-10-13.json").get("entries").size());
    }

    @Test
    void flushIsSafeConcurrentlyWithChatAndMoves() throws Exception {
        int threads = 4, per = 25;
        List<Thread> ts = new ArrayList<>();
        var errors = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        for (int t = 0; t < threads; t++) {
            int n = t;
            ts.add(new Thread(() -> {
                try {
                    for (int i = 0; i < per; i++) {
                        archive.chat("u" + n, ann, "m" + n + "-" + i);
                        String id = archive.startMove("u" + n, Level.GUEST, ann, "s", three(3), new RobotState());
                        archive.finishMove(id, "done", new RobotState(), three(3));
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            }));
        }
        Thread flusher = new Thread(() -> {
            try {
                for (int i = 0; i < 40; i++) {
                    archive.flush();
                    Thread.sleep(1);
                }
            } catch (Throwable e) {
                errors.add(e);
            }
        });
        ts.forEach(Thread::start);
        flusher.start();
        for (Thread t : ts) t.join();
        flusher.join();
        archive.flush();
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(threads * per * 2, readJson("public/index/2026-10-13.json").get("entries").size());
    }

    /** A store that can be slowed down or made to fail. */
    static final class FlakyStore implements ArchiveStore {
        final ArchiveStore inner;
        volatile boolean failGets, failPuts;
        volatile long delayMs;

        FlakyStore(ArchiveStore inner) {
            this.inner = inner;
        }

        void pause() {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void put(String key, byte[] body, String contentType) {
            pause();
            if (failPuts) throw new IllegalStateException("put failed");
            inner.put(key, body, contentType);
        }

        public Optional<byte[]> get(String key) {
            pause();
            if (failGets) throw new IllegalStateException("get failed");
            return inner.get(key);
        }

        public void delete(String key) {
            inner.delete(key);
        }

        public long bytesUsed() {
            return inner.bytesUsed();
        }
    }

    @Test
    void concurrentFlushersWriteEveryEntryAndAuditLineExactlyOnceInOrder() throws Exception {
        FlakyStore slow = new FlakyStore(store);
        slow.delayMs = 2;
        Archive a = new Archive(slow, () -> now, 5_000_000_000L, new Random(5));
        List<String> ids = new ArrayList<>();
        var errors = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        Runnable flusher = () -> {
            try {
                for (int i = 0; i < 15; i++) a.flush();
            } catch (Throwable e) {
                errors.add(e);
            }
        };
        Thread f1 = new Thread(flusher), f2 = new Thread(flusher);
        f1.start();
        f2.start();
        for (int i = 0; i < 40; i++) {
            now += 1;
            ids.add(a.chat("ann", ann, "m" + i));
            if (i % 10 == 9) assertTrue(a.delete("chat", ids.get(i - 1))); // delete flushes inline too
        }
        f1.join();
        f2.join();
        a.flush();
        assertTrue(errors.isEmpty(), errors.toString());

        JsonNode entries = readJson("public/index/2026-10-13.json").get("entries");
        assertEquals(36, entries.size());
        List<String> lines = new String(store.get("private/audit/2026-10-13.jsonl").orElseThrow(), StandardCharsets.UTF_8)
                .strip().lines().toList();
        List<String> chatIds = new ArrayList<>();
        int deletes = 0;
        for (String l : lines) {
            JsonNode n = JSON.readTree(l);
            if (n.get("what").asText().equals("chat")) chatIds.add(n.get("id").asText());
            else deletes++;
        }
        assertEquals(ids, chatIds); // each once, in order
        assertEquals(4, deletes);
        assertEquals(44, lines.size());
    }

    @Test
    void transientReadFailureNeverOverwritesTheStoredDay() throws IOException {
        archive.chat("ann", ann, "old");
        archive.flush();
        String before = new String(store.get("public/index/2026-10-13.json").orElseThrow(), StandardCharsets.UTF_8);

        FlakyStore flaky = new FlakyStore(store);
        flaky.failGets = true;
        Archive b = new Archive(flaky, () -> now + 1000, 5_000_000_000L, new Random(8));
        b.chat("bob", ann, "new");
        b.flush();
        assertEquals(before, new String(store.get("public/index/2026-10-13.json").orElseThrow(), StandardCharsets.UTF_8));
        assertEquals(1, readJson("public/index/days.json").get("days").size());

        flaky.failGets = false;
        b.flush();
        JsonNode entries = readJson("public/index/2026-10-13.json").get("entries");
        assertEquals(List.of("old", "new"), texts2(entries));
        String audit = new String(store.get("private/audit/2026-10-13.jsonl").orElseThrow(), StandardCharsets.UTF_8);
        assertEquals(2, audit.strip().lines().count());
    }

    @Test
    void corruptIndexIsQuarantinedThenStartedFresh() throws IOException {
        store.put("public/index/2026-10-13.json", "{not json".getBytes(StandardCharsets.UTF_8), "application/json");
        archive.chat("ann", ann, "hi");
        archive.flush();
        assertEquals(1, readJson("public/index/2026-10-13.json").get("entries").size());
        List<String> bad = files().stream().filter(f -> f.startsWith("private/corrupt/2026-10-13-")).toList();
        assertEquals(1, bad.size());
        assertEquals("{not json", new String(Files.readAllBytes(dir.resolve(bad.get(0))), StandardCharsets.UTF_8));
    }

    @Test
    void failedFlushKeepsEntriesPendingUntilTheNextSuccessfulFlush() throws IOException {
        FlakyStore flaky = new FlakyStore(store);
        Archive a = new Archive(flaky, () -> now, 5_000_000_000L, new Random(2));
        a.chat("ann", ann, "keep");
        flaky.failPuts = true;
        a.flush();
        assertTrue(store.get("public/index/2026-10-13.json").isEmpty());
        flaky.failPuts = false;
        a.flush();
        assertEquals(List.of("keep"), texts2(readJson("public/index/2026-10-13.json").get("entries")));
        assertEquals(1, new String(store.get("private/audit/2026-10-13.jsonl").orElseThrow(), StandardCharsets.UTF_8).strip().lines().count());
        assertEquals(List.of("2026-10-13"), texts(readJson("public/index/days.json").get("days")));
    }

    @Test
    void nonFiniteCalibrationSkipsTheFrame() throws IOException {
        ImageData bad = img(5);
        bad.calibrationMatrix.set(1, 1, Double.NaN);
        String id = archive.startMove("ann", Level.GUEST, ann, "x", Map.of("Colour", bad), null);
        archive.finishMove(id, "done", null, null);
        archive.flush();
        assertEquals(0, count(".jpg"));
        assertFalse(readJson("public/index/2026-10-13.json").get("entries").get(0).get("frames").has("colour"));
    }

    @Test
    void deleteRejectsIdsOutsideTheSaneTimeRange() {
        assertFalse(archive.delete("chat", "1-aaaa"));
        assertFalse(archive.delete("chat", "99999999999999-aaaa"));
    }

    @Test
    void fileStoreRejectsKeyThatIsTheRoot() {
        assertThrows(IllegalArgumentException.class, () -> store.put(".", new byte[1], "x"));
        assertThrows(IllegalArgumentException.class, () -> store.put("a/..", new byte[1], "x"));
    }

    @Test
    void s3CacheControl() {
        assertEquals("public, max-age=31536000, immutable", S3ArchiveStore.cacheControl("public/moves/x.jpg", "image/jpeg"));
        assertEquals("public, max-age=30", S3ArchiveStore.cacheControl("public/index/days.json", "application/json"));
        assertEquals("private, no-store", S3ArchiveStore.cacheControl("private/audit/d.jsonl", "application/x-ndjson"));
    }

    static List<String> texts2(JsonNode entries) {
        List<String> out = new ArrayList<>();
        entries.forEach(n -> out.add(n.get("text").asText()));
        return out;
    }

    static List<String> texts(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }
}
