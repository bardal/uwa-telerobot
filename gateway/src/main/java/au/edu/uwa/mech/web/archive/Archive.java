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
import java.util.function.LongSupplier;

/**
 * The public record of what happened in the lab: moves with before and after frames, snapshots,
 * and chat, grouped into one JSON index per UTC day plus {@code days.json}. Who did it (sub and
 * IP hash) goes only to the private audit log.
 *
 * <p>All state is guarded by one lock; store I/O for images and for {@link #flush()} happens
 * outside it, so slow S3 calls never block chat. Image writes are skipped once the store passes
 * the byte cap, but chat and move entries are always kept.
 */
public final class Archive {
    static final int SNAPSHOTS_PER_HOUR = 10;
    static final long HOUR_MS = 3_600_000L;
    private static final String JPEG = "image/jpeg";
    private static final String JSON_TYPE = "application/json";
    private static final String BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final System.Logger LOG = System.getLogger(Archive.class.getName());

    private final ArchiveStore store;
    private final LongSupplier clock;
    private final long capBytes;
    private final Random random;

    private final Object lock = new Object();
    private final Map<String, ObjectNode> days = new HashMap<>(); // day -> {"day":..,"entries":[..]}
    private final Set<String> touched = new HashSet<>();
    private final Map<String, StringBuilder> auditPending = new HashMap<>(); // day -> jsonl lines
    private final Map<String, Deque<Long>> snapshotTimes = new HashMap<>(); // sub -> recent snapshot times
    private final Map<String, ObjectNode> openMoves = new HashMap<>(); // moveId -> index entry
    private TreeSet<String> knownDays; // loaded lazily from days.json

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
            addEntry(Keys.day(time), e);
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
        String sub = id == null ? "" : String.valueOf(id.sub());
        String snapId;
        synchronized (lock) {
            if (capped()) return Optional.empty();
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
            addEntry(Keys.day(time), e);
            audit(time, "snapshot", snapId, nick, id, null, null);
        }
        return Optional.of(snapId);
    }

    // ---- chat ----

    public String chat(String nick, Identity id, String text) {
        long time = clock.getAsLong();
        synchronized (lock) {
            String chatId = newId(time);
            ObjectNode e = MAPPER.createObjectNode();
            e.put("kind", "chat").put("id", chatId).put("time", time).put("nick", nick == null ? "" : nick);
            e.put("text", text == null ? "" : text);
            addEntry(Keys.day(time), e);
            audit(time, "chat", chatId, nick, id, "text", text);
            return chatId;
        }
    }

    // ---- flush ----

    /** Writes the touched day indexes, days.json and new audit lines. Safe to call at any time. */
    public void flush() {
        Map<String, byte[]> indexes = new LinkedHashMap<>();
        Map<String, String> audits;
        byte[] daysJson = null;
        synchronized (lock) {
            if (touched.isEmpty() && auditPending.isEmpty()) return;
            loadKnownDays();
            for (String day : touched) {
                ObjectNode d = days.get(day);
                if (d == null) continue;
                sortEntries((ArrayNode) d.get("entries"));
                indexes.put(day, bytes(d));
                knownDays.add(day);
            }
            if (!touched.isEmpty()) daysJson = daysJson();
            audits = new LinkedHashMap<>();
            auditPending.forEach((day, sb) -> audits.put(day, sb.toString()));
            touched.clear();
            auditPending.clear();
        }
        // I/O outside the lock; anything that fails is queued again for the next flush
        Set<String> failedDays = new HashSet<>();
        Map<String, String> failedAudits = new LinkedHashMap<>();
        for (var en : indexes.entrySet()) {
            try {
                store.put(Keys.dayIndex(en.getKey()), en.getValue(), JSON_TYPE);
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.WARNING, "archive index write failed: " + en.getKey(), ex);
                failedDays.add(en.getKey());
            }
        }
        if (daysJson != null) {
            try {
                store.put(Keys.DAYS, daysJson, JSON_TYPE);
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.WARNING, "archive days.json write failed", ex);
                failedDays.addAll(indexes.keySet());
            }
        }
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
                // keep older lines ahead of any queued since
                failedAudits.forEach((day, lines) ->
                        auditPending.computeIfAbsent(day, k -> new StringBuilder()).insert(0, lines));
            }
        }
    }

    // ---- delete ----

    /** Removes a move, snapshot or chat entry and its images. False if there was no such entry. */
    public boolean delete(String kind, String id) {
        if (kind == null || !(kind.equals("move") || kind.equals("snapshot") || kind.equals("chat"))) return false;
        long time = Keys.idTime(id);
        if (time < 0) return false;
        String day = Keys.day(time);
        List<String> keys = new ArrayList<>();
        synchronized (lock) {
            ObjectNode d = dayIndex(day);
            ArrayNode entries = (ArrayNode) d.get("entries");
            int at = -1;
            for (int i = 0; i < entries.size(); i++) {
                JsonNode n = entries.get(i);
                if (kind.equals(n.path("kind").asText()) && id.equals(n.path("id").asText())) {
                    at = i;
                    break;
                }
            }
            if (at < 0) return false;
            collectKeys(entries.get(at).path("frames"), keys);
            entries.remove(at);
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

    private static void collectKeys(JsonNode n, List<String> out) {
        if (n.isTextual()) {
            out.add(n.asText());
        } else if (n.isObject()) {
            n.fields().forEachRemaining(f -> {
                if (!f.getKey().equals("calibration")) collectKeys(f.getValue(), out);
            });
        }
    }

    // ---- internals (call under lock where they touch state) ----

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

    private void addEntry(String day, ObjectNode e) {
        ((ArrayNode) dayIndex(day).get("entries")).add(e);
        touched.add(day);
    }

    /** The in-memory index for a day, loading what an earlier run stored. */
    private ObjectNode dayIndex(String day) {
        ObjectNode d = days.get(day);
        if (d != null) return d;
        d = null;
        try {
            Optional<byte[]> old = store.get(Keys.dayIndex(day));
            if (old.isPresent()) {
                JsonNode n = MAPPER.readTree(old.get());
                if (n instanceof ObjectNode on && on.get("entries") instanceof ArrayNode) d = on;
            }
        } catch (IOException | RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "archive index unreadable: " + day, ex);
        }
        if (d == null) {
            d = MAPPER.createObjectNode();
            d.put("day", day);
            d.putArray("entries");
        }
        days.put(day, d);
        return d;
    }

    private void loadKnownDays() {
        if (knownDays != null) return;
        knownDays = new TreeSet<>();
        try {
            Optional<byte[]> old = store.get(Keys.DAYS);
            if (old.isPresent()) {
                for (JsonNode n : MAPPER.readTree(old.get()).path("days")) knownDays.add(n.asText());
            }
        } catch (IOException | RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "days.json unreadable", ex);
        }
    }

    private byte[] daysJson() {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode arr = root.putArray("days");
        for (String d : knownDays.descendingSet()) arr.add(d);
        return bytes(root);
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

    /** The 3x4 calibration matrix as 12 numbers, row by row; null if absent or the wrong shape. */
    private static ArrayNode calibration(ImageData d) {
        if (d == null || d.calibrationMatrix == null) return null;
        double[][] m = d.calibrationMatrix.getArray();
        if (m == null || m.length != 3) return null;
        ArrayNode a = MAPPER.createArrayNode();
        for (double[] row : m) {
            if (row == null || row.length != 4) return null;
            for (double v : row) a.add(v);
        }
        return a;
    }
}
