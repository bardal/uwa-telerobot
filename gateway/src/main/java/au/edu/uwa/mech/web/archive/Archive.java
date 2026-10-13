package au.edu.uwa.mech.web.archive;

import au.edu.uwa.mech.devices.camera.ImageData;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.web.Identity;
import au.edu.uwa.mech.web.Identity.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * The public record of what happened in the lab: moves with before and after frames, snapshots,
 * and chat, grouped into one JSON index per UTC day plus {@code days.json}. Who did it (sub and
 * IP hash) goes only to the private audit log.
 *
 * <p>Locking: {@code flushLock} serialises whole flushes (acquired before {@code lock}); {@code lock}
 * guards in-memory state and is never held across store I/O. A day whose stored index could not be
 * read is left unloaded: new entries for it are buffered and the day's index is never written until
 * the load succeeds, so a transient read error cannot overwrite the real index.
 */
public final class Archive {
    static final int SNAPSHOTS_PER_HOUR = 10;
    static final long HOUR_MS = 3_600_000L;
    static final long MIN_ID_TIME = 1_577_836_800_000L; // 2020-01-01
    static final long MAX_ID_TIME = 4_102_444_800_000L; // 2100-01-01
    private static final String JPEG = "image/jpeg";
    private static final String JSON_TYPE = "application/json";
    private static final String BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final System.Logger LOG = System.getLogger(Archive.class.getName());

    private final ArchiveStore store;
    private final LongSupplier clock;
    private final long capBytes;
    private final Random random;

    private final ReentrantLock flushLock = new ReentrantLock();
    private final Object lock = new Object();
    private final Map<String, ObjectNode> days = new HashMap<>(); // loaded days: {"day":..,"entries":[..]}
    private final Map<String, List<ObjectNode>> pending = new HashMap<>(); // entries for days not loaded yet
    private final Set<String> touched = new HashSet<>();
    private final Map<String, StringBuilder> auditPending = new HashMap<>(); // day -> jsonl lines
    private final Map<String, Deque<Long>> snapshotTimes = new HashMap<>(); // sub -> recent snapshot times
    private final Map<String, ObjectNode> openMoves = new HashMap<>(); // moveId -> index entry
    private TreeSet<String> knownDays; // loaded from days.json; null until it has loaded
    private final Set<String> daysToAdd = new HashSet<>(); // days whose index is written but not yet in days.json

    public Archive(ArchiveStore store, LongSupplier clock, long capBytes) {
        this(store, clock, capBytes, new Random());
    }

    public Archive(ArchiveStore store, LongSupplier clock, long capBytes, Random random) {
        this.store = store;
        this.clock = clock;
        this.capBytes = capBytes;
        this.random = random;
    }

    // ---- moves ----

    public String startMove(String nick, Level lvl, Identity id, String script,
                            Map<String, ImageData> lastFrames, RobotState before) {
        long time = clock.getAsLong();
        String day = Keys.day(time);
        ensureLoaded(day);
        String moveId;
        synchronized (lock) {
            moveId = newId(time);
        }
        ObjectNode frames = MAPPER.createObjectNode();
        putMoveFrames(frames, moveId, time, lastFrames, "before");

        ObjectNode e = MAPPER.createObjectNode();
        e.put("kind", "move").put("id", moveId).put("time", time).put("nick", nick == null ? "" : nick);
        e.put("level", (lvl == null ? Level.GUEST : lvl).name().toLowerCase(Locale.ROOT));
        e.put("script", script == null ? "" : script);
        e.put("result", "pending");
        e.set("poseBefore", pose(before));
        e.set("frames", frames);
        synchronized (lock) {
            openMoves.put(moveId, e);
            addEntry(day, e);
            audit(time, "move", moveId, nick, id, "script", script);
        }
        return moveId;
    }

    public void finishMove(String moveId, String result, RobotState after, Map<String, ImageData> afterFrames) {
        ObjectNode e;
        synchronized (lock) {
            e = openMoves.get(moveId);
        }
        if (e == null) return;
        long time = e.get("time").asLong();
        ObjectNode scratch = MAPPER.createObjectNode();
        putMoveFrames(scratch, moveId, time, afterFrames, "after");
        synchronized (lock) {
            if (openMoves.remove(moveId) == null) return; // deleted meanwhile
            ObjectNode frames = (ObjectNode) e.get("frames");
            scratch.fields().forEachRemaining(f -> {
                ObjectNode have = (ObjectNode) frames.get(f.getKey());
                if (have == null) {
                    frames.set(f.getKey(), f.getValue());
                } else {
                    have.set("after", f.getValue().get("after"));
                    if (!have.has("calibration")) have.set("calibration", f.getValue().get("calibration"));
                }
            });
            e.put("result", result == null ? "" : result);
            e.set("poseAfter", pose(after));
            touched.add(Keys.day(time));
        }
    }

    /** Writes the images for one phase and records them in {@code frames} (camera -> {phase, calibration}). */
    private void putMoveFrames(ObjectNode frames, String moveId, long time, Map<String, ImageData> images, String phase) {
        if (images == null) return;
        for (var en : images.entrySet()) {
            String cam = Keys.camera(en.getKey());
            ImageData d = en.getValue();
            ArrayNode cal = calibration(d);
            if (cam.isEmpty() || cal == null || d.pixels == null || d.pixels.length == 0) continue;
            String key = Keys.moveImage(moveId, time, cam, phase);
            if (!putImage(key, d.pixels)) continue;
            ObjectNode f = frames.has(cam) ? (ObjectNode) frames.get(cam) : frames.putObject(cam);
            f.put(phase, key);
            if (!f.has("calibration")) f.set("calibration", cal);
        }
    }

    // ---- snapshots ----

    /** Empty when the archive is full or the user has taken 10 snapshots in the last hour. */
    public Optional<String> snapshot(String nick, Identity id, Map<String, ImageData> frames, RobotState pose) {
        long time = clock.getAsLong();
        String day = Keys.day(time);
        String sub = id == null ? "" : String.valueOf(id.sub());
        if (capped()) return Optional.empty();
        ensureLoaded(day);
        String snapId;
        synchronized (lock) {
            // forget users whose snapshots are all more than an hour old
            snapshotTimes.values().removeIf(q -> q.isEmpty() || q.peekLast() <= time - HOUR_MS);
            Deque<Long> q = snapshotTimes.computeIfAbsent(sub, k -> new ArrayDeque<>());
            while (!q.isEmpty() && q.peekFirst() <= time - HOUR_MS) q.pollFirst();
            if (q.size() >= SNAPSHOTS_PER_HOUR) return Optional.empty();
            q.addLast(time);
            snapId = newId(time);
        }
        ObjectNode fr = MAPPER.createObjectNode();
        if (frames != null) {
            for (var en : frames.entrySet()) {
                String cam = Keys.camera(en.getKey());
                ImageData d = en.getValue();
                if (cam.isEmpty() || d == null || d.pixels == null || d.pixels.length == 0) continue;
                String key = Keys.snapshotImage(snapId, time, cam);
                if (putImage(key, d.pixels)) fr.put(cam, key);
            }
        }
        ObjectNode e = MAPPER.createObjectNode();
        e.put("kind", "snapshot").put("id", snapId).put("time", time).put("nick", nick == null ? "" : nick);
        e.set("pose", pose(pose));
        e.set("frames", fr);
        synchronized (lock) {
            addEntry(day, e);
            audit(time, "snapshot", snapId, nick, id, null, null);
        }
        return Optional.of(snapId);
    }

    // ---- chat ----

    public String chat(String nick, Identity id, String text) {
        long time = clock.getAsLong();
        String day = Keys.day(time);
        ensureLoaded(day);
        synchronized (lock) {
            String chatId = newId(time);
            ObjectNode e = MAPPER.createObjectNode();
            e.put("kind", "chat").put("id", chatId).put("time", time).put("nick", nick == null ? "" : nick);
            e.put("text", text == null ? "" : text);
            addEntry(day, e);
            audit(time, "chat", chatId, nick, id, "text", text);
            return chatId;
        }
    }

    // ---- flush ----

    /**
     * Writes the touched day indexes, days.json and new audit lines. Safe to call at any time and from
     * several threads: flushes run one at a time. Anything that fails stays queued for the next flush.
     */
    public void flush() {
        flushLock.lock();
        try {
            doFlush();
        } finally {
            flushLock.unlock();
        }
    }

    private void doFlush() {
        // 1. try to load any touched day that is still unloaded (outside the state lock)
        Set<String> want;
        synchronized (lock) {
            want = new HashSet<>(touched);
        }
        for (String day : want) ensureLoaded(day);

        // 2. serialise what is ready
        Map<String, byte[]> indexes = new LinkedHashMap<>();
        Map<String, String> audits = new LinkedHashMap<>();
        synchronized (lock) {
            for (String day : new ArrayList<>(touched)) {
                ObjectNode d = days.get(day);
                if (d == null) continue; // not loaded: keep touched, never write it
                sortEntries((ArrayNode) d.get("entries"));
                indexes.put(day, bytes(d));
                touched.remove(day);
            }
            auditPending.forEach((day, sb) -> audits.put(day, sb.toString()));
            auditPending.clear();
        }

        // 3. write (flushLock keeps other flushes out; the state lock is free for chat and moves)
        Set<String> failedDays = new HashSet<>();
        for (var en : indexes.entrySet()) {
            try {
                store.put(Keys.dayIndex(en.getKey()), en.getValue(), JSON_TYPE);
                daysToAdd.add(en.getKey());
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.WARNING, "archive index write failed: " + en.getKey(), ex);
                failedDays.add(en.getKey());
            }
        }
        writeDaysJson();

        Map<String, String> failedAudits = new LinkedHashMap<>();
        for (var en : audits.entrySet()) {
            try {
                String key = Keys.audit(en.getKey());
                byte[] old = store.get(key).orElse(new byte[0]);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.writeBytes(old);
                out.writeBytes(en.getValue().getBytes(StandardCharsets.UTF_8));
                store.put(key, out.toByteArray(), "application/x-ndjson");
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.WARNING, "archive audit write failed: " + en.getKey(), ex);
                failedAudits.put(en.getKey(), en.getValue());
            }
        }
        if (!failedDays.isEmpty() || !failedAudits.isEmpty()) {
            synchronized (lock) {
                touched.addAll(failedDays);
                // older lines go ahead of any queued since
                failedAudits.forEach((day, lines) ->
                        auditPending.computeIfAbsent(day, k -> new StringBuilder()).insert(0, lines));
            }
        }
    }

    /** Adds the days whose index is safely written to days.json (newest first). Only under flushLock. */
    private void writeDaysJson() {
        if (daysToAdd.isEmpty()) return;
        if (knownDays == null) {
            TreeSet<String> loaded = loadKnownDays();
            if (loaded == null) return; // retry next flush; never overwrite an unread days.json
            knownDays = loaded;
        }
        TreeSet<String> all = new TreeSet<>(knownDays);
        all.addAll(daysToAdd);
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode arr = root.putArray("days");
        for (String d : all.descendingSet()) arr.add(d);
        try {
            store.put(Keys.DAYS, bytes(root), JSON_TYPE);
            knownDays = all;
            daysToAdd.clear();
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "archive days.json write failed", ex);
        }
    }

    /** Reads days.json; an absent file is an empty set, a read failure is null, corrupt JSON is quarantined. */
    private TreeSet<String> loadKnownDays() {
        TreeSet<String> out = new TreeSet<>();
        Optional<byte[]> old;
        try {
            old = store.get(Keys.DAYS);
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "days.json unreadable, will retry", ex);
            return null;
        }
        if (old.isEmpty()) return out;
        try {
            JsonNode n = MAPPER.readTree(old.get());
            if (!(n instanceof ObjectNode) || !n.path("days").isArray()) throw new IOException("not a days object");
            for (JsonNode d : n.get("days")) out.add(d.asText());
            return out;
        } catch (IOException ex) {
            if (!quarantine("days", old.get())) return null;
            LOG.log(System.Logger.Level.ERROR, "days.json corrupt, starting fresh", ex);
            return out;
        }
    }

    // ---- delete ----

    /** Removes a move, snapshot or chat entry and its images. False if there was no such entry. */
    public boolean delete(String kind, String id) {
        if (kind == null || !(kind.equals("move") || kind.equals("snapshot") || kind.equals("chat"))) return false;
        long time = Keys.idTime(id);
        if (time < MIN_ID_TIME || time > MAX_ID_TIME) return false;
        String day = Keys.day(time);
        ensureLoaded(day);
        List<String> keys = new ArrayList<>();
        synchronized (lock) {
            List<JsonNode> entries = new ArrayList<>();
            ObjectNode d = days.get(day);
            if (d != null) d.get("entries").forEach(entries::add);
            List<ObjectNode> buffered = pending.get(day);
            if (buffered != null) entries.addAll(buffered);
            JsonNode hit = null;
            for (JsonNode n : entries) {
                if (kind.equals(n.path("kind").asText()) && id.equals(n.path("id").asText())) {
                    hit = n;
                    break;
                }
            }
            if (hit == null) return false;
            collectKeys(hit.path("frames"), keys);
            final JsonNode target = hit;
            if (d != null) removeNode((ArrayNode) d.get("entries"), target);
            if (buffered != null) buffered.removeIf(n -> n == target);
            openMoves.remove(id);
            touched.add(day);
            audit(clock.getAsLong(), "delete-" + kind, id, null, null, null, null);
        }
        for (String k : keys) {
            try {
                store.delete(k);
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.WARNING, "archive delete failed: " + k, ex);
            }
        }
        flush();
        return true;
    }

    private static void removeNode(ArrayNode arr, JsonNode target) {
        for (int i = 0; i < arr.size(); i++) {
            if (arr.get(i) == target) {
                arr.remove(i);
                return;
            }
        }
    }

    private static void collectKeys(JsonNode n, List<String> out) {
        if (n.isTextual()) {
            out.add(n.asText());
        } else if (n.isObject()) {
            n.fields().forEachRemaining(f -> {
                if (!f.getKey().equals("calibration")) collectKeys(f.getValue(), out);
            });
        }
    }

    // ---- internals ----

    private boolean capped() {
        return store.bytesUsed() >= capBytes;
    }

    private boolean putImage(String key, byte[] pixels) {
        try {
            if (store.bytesUsed() + pixels.length > capBytes) return false;
            store.put(key, pixels, JPEG);
            return true;
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "archive image write failed: " + key, ex);
            return false;
        }
    }

    private String newId(long time) {
        StringBuilder sb = new StringBuilder().append(time).append('-');
        for (int i = 0; i < 4; i++) sb.append(BASE36.charAt(random.nextInt(36)));
        return sb.toString();
    }

    /** Under lock. Entries for an unloaded day wait in {@code pending} until its index has loaded. */
    private void addEntry(String day, ObjectNode e) {
        ObjectNode d = days.get(day);
        if (d != null) {
            ((ArrayNode) d.get("entries")).add(e);
        } else {
            pending.computeIfAbsent(day, k -> new ArrayList<>()).add(e);
        }
        touched.add(day);
    }

    /**
     * Loads a day's stored index if it is not loaded yet. Store I/O happens outside the state lock; the
     * result is installed under it (first installer wins, buffered entries are appended). A read failure
     * leaves the day unloaded so a later call retries.
     */
    private void ensureLoaded(String day) {
        synchronized (lock) {
            if (days.containsKey(day)) return;
        }
        ObjectNode loaded = loadDay(day);
        if (loaded == null) return;
        synchronized (lock) {
            if (days.containsKey(day)) return;
            List<ObjectNode> buffered = pending.remove(day);
            if (buffered != null) {
                ArrayNode entries = (ArrayNode) loaded.get("entries");
                buffered.forEach(entries::add);
            }
            days.put(day, loaded);
        }
    }

    /** The stored index (an empty one only if genuinely absent or corrupt and quarantined); null on read failure. */
    private ObjectNode loadDay(String day) {
        Optional<byte[]> old;
        try {
            old = store.get(Keys.dayIndex(day));
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "archive index unreadable, will retry: " + day, ex);
            return null;
        }
        if (old.isPresent()) {
            try {
                JsonNode n = MAPPER.readTree(old.get());
                if (n instanceof ObjectNode on && on.get("entries") instanceof ArrayNode) return on;
                throw new IOException("not an index object");
            } catch (IOException ex) {
                if (!quarantine(day, old.get())) return null;
                LOG.log(System.Logger.Level.ERROR, "archive index corrupt, starting fresh: " + day, ex);
            }
        }
        ObjectNode d = MAPPER.createObjectNode();
        d.put("day", day);
        d.putArray("entries");
        return d;
    }

    /** Copies a bad object aside before it is replaced. False if the copy failed (then nothing is replaced). */
    private boolean quarantine(String name, byte[] bad) {
        try {
            store.put("private/corrupt/" + name + "-" + clock.getAsLong() + ".json", bad, JSON_TYPE);
            return true;
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.ERROR, "could not quarantine corrupt object: " + name, ex);
            return false;
        }
    }

    private static void sortEntries(ArrayNode entries) {
        List<JsonNode> list = new ArrayList<>();
        entries.forEach(list::add);
        list.sort(Comparator.comparingLong(n -> n.path("time").asLong())); // stable: ties keep arrival order
        entries.removeAll();
        list.forEach(entries::add);
    }

    private static byte[] bytes(JsonNode n) {
        try {
            return MAPPER.writeValueAsBytes(n);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void audit(long time, String what, String id, String nick, Identity who, String extraKey, String extra) {
        ObjectNode a = MAPPER.createObjectNode();
        a.put("time", time).put("what", what).put("id", id);
        if (nick != null) a.put("nick", nick);
        if (who != null) {
            a.put("sub", who.sub()).put("ipHash", who.ipHash());
            if (who.level() != null) a.put("level", who.level().name().toLowerCase(Locale.ROOT));
        }
        if (extraKey != null) a.put(extraKey, extra == null ? "" : extra);
        auditPending.computeIfAbsent(Keys.day(time), k -> new StringBuilder()).append(a).append('\n');
    }

    private static ObjectNode pose(RobotState s) {
        ObjectNode p = MAPPER.createObjectNode();
        if (s == null || s.pose == null) return p;
        p.put("x", s.pose.position.x).put("y", s.pose.position.y).put("z", s.pose.position.z);
        p.put("spin", s.pose.orientation.spinDeg()).put("tilt", s.pose.orientation.tiltDeg());
        p.put("gripperOpen", s.gripper != null && s.gripper.open());
        return p;
    }

    /**
     * The 3x4 calibration matrix as 12 numbers, row by row. Null if absent, the wrong shape, or any value is
     * NaN or infinite: the History page needs all 12 finite numbers, so such a frame is skipped.
     */
    private static ArrayNode calibration(ImageData d) {
        if (d == null || d.calibrationMatrix == null) return null;
        double[][] m = d.calibrationMatrix.getArray();
        if (m == null || m.length != 3) return null;
        ArrayNode a = MAPPER.createArrayNode();
        for (double[] row : m) {
            if (row == null || row.length != 4) return null;
            for (double v : row) {
                if (!Double.isFinite(v)) return null;
                a.add(v);
            }
        }
        return a;
    }
}
