package au.edu.uwa.mech.web;

import au.edu.uwa.mech.sim.PathScript;
import au.edu.uwa.mech.sim.RobotPose;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Per-session message limits, text hygiene and rate buckets. One instance per session; thread-safe. */
public final class Limits {
    public static final int MAX_SCRIPT_CHARS = 400;
    public static final int MAX_WAYPOINTS = 30;
    public static final int MAX_CHAT_CHARS = 300;
    public static final int REJECTIONS_TO_CLOSE = 20;
    public static final long REJECTION_WINDOW_MILLIS = 60_000;

    private static final Pattern TERM_SPLIT = Pattern.compile("[;, \t\r\n]+");
    /** Terms PathScript understands: axis values (optionally relative), tilt presets, gripper words, bare o/c. */
    private static final Pattern TERM = Pattern.compile(
        "r?[xyzst][+-]?\\d+(\\.\\d+)?|t[fvb]|g[A-Za-z0-9]*|[oOcC]");
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9 _.-]{1,20}");
    private static final RobotPose ORIGIN = new RobotPose(0, 0, 0, 0, 0, true);

    /** Outcome of a check. For chat, {@code text} is the cleaned message. */
    public record Verdict(boolean ok, String reason, String text) {
        static Verdict ok(String text) { return new Verdict(true, null, text); }
        static Verdict refuse(String reason) { return new Verdict(false, reason, null); }
    }

    private record Spec(double capacity, double perMillis) {}

    private static final Map<String, Spec> SPECS = Map.of(
        "chat", new Spec(5, 1 / 1000.0),                 // 1 a second, burst of 5
        "command", new Spec(10, 5 / 1000.0),             // 5 a second, burst of 10
        "activity", new Spec(2, 2 / 1000.0),             // 2 a second
        "snapshot", new Spec(10, 10 / 3_600_000.0),      // 10 an hour
        "camera", new Spec(3, 1 / 1000.0),               // newImage, cameraSettings, selectCamera: 1 a second, burst of 3
        "any", new Spec(60, 30 / 1000.0));               // backstop for every message

    private static final class Bucket {
        final Spec spec;
        double tokens;
        long last;
        Bucket(Spec spec, long now) { this.spec = spec; this.tokens = spec.capacity; this.last = now; }
        boolean take(long now) {
            tokens = Math.min(spec.capacity, tokens + (now - last) * spec.perMillis);
            last = now;
            if (tokens < 1 - 1e-9) return false;
            tokens -= 1;
            return true;
        }
    }

    private final LongSupplier clock;
    private final Blocklist blocklist;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final ArrayDeque<Long> rejections = new ArrayDeque<>();

    public Limits(LongSupplier clock) {
        this(clock, Blocklist.defaults());
    }

    public Limits(LongSupplier clock, Blocklist blocklist) {
        this.clock = clock;
        this.blocklist = blocklist;
    }

    /** Length, then syntax (and the parser), then waypoint count. */
    public Verdict checkScript(String s) {
        if (s == null || s.isBlank()) return Verdict.refuse("script is empty");
        if (s.length() > MAX_SCRIPT_CHARS) return Verdict.refuse("script is longer than " + MAX_SCRIPT_CHARS + " characters");
        // PathScript ignores what it does not recognise, so check every term against the forms it accepts.
        for (String term : TERM_SPLIT.split(s)) {
            if (!term.isEmpty() && !TERM.matcher(term).matches())
                return Verdict.refuse("script: unrecognised term '" + printable(term) + "'");
        }
        List<RobotPose> path;
        try {
            path = PathScript.parse(s, ORIGIN);
        } catch (RuntimeException e) {
            return Verdict.refuse("script: " + e.getMessage());
        }
        if (path.isEmpty()) return Verdict.refuse("script has no waypoints");
        if (path.size() > MAX_WAYPOINTS) return Verdict.refuse("script has more than " + MAX_WAYPOINTS + " waypoints");
        return Verdict.ok(s);
    }

    private static String printable(String t) {
        String c = t.length() > 20 ? t.substring(0, 20) + "..." : t;
        return c.replaceAll("\\p{Cntrl}", "?");
    }

    public Verdict checkChat(String s) {
        if (s == null) return Verdict.refuse("message is empty");
        String n = Normalizer.normalize(s, Normalizer.Form.NFC);
        if (n.codePointCount(0, n.length()) > MAX_CHAT_CHARS)
            return Verdict.refuse("message is longer than " + MAX_CHAT_CHARS + " characters");
        StringBuilder sb = new StringBuilder(n.length());
        n.codePoints().forEach(cp -> {
            boolean bidi = (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069);
            if (!bidi && !Character.isISOControl(cp)) sb.appendCodePoint(cp);
        });
        String clean = sb.toString().strip();
        if (clean.isEmpty()) return Verdict.refuse("message is empty");
        if (blocklist.blocks(clean)) return Verdict.refuse("message not allowed");
        return Verdict.ok(clean);
    }

    public static Optional<String> cleanNick(String raw) {
        if (raw == null) return Optional.empty();
        String t = raw.strip();
        return NICK.matcher(t).matches() ? Optional.of(t) : Optional.empty();
    }

    /** Takes a token from the named bucket; false when the bucket is empty. */
    public synchronized boolean allow(String bucket) {
        Spec spec = SPECS.get(bucket);
        if (spec == null) throw new IllegalArgumentException("unknown bucket " + bucket);
        long now = clock.getAsLong();
        return buckets.computeIfAbsent(bucket, k -> new Bucket(spec, now)).take(now);
    }

    /** Records a rejected message; true when the session must close. */
    public synchronized boolean recordRejection() {
        long now = clock.getAsLong();
        rejections.addLast(now);
        while (!rejections.isEmpty() && now - rejections.peekFirst() >= REJECTION_WINDOW_MILLIS)
            rejections.removeFirst();
        return rejections.size() >= REJECTIONS_TO_CLOSE;
    }
}
