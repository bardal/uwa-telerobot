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

    public static final int MAX_NUMBER_DIGITS = 6;
    private static final Pattern LINE_BREAKS = Pattern.compile("[\r\n\t]+");
    private static final Pattern LONG_NUMBER = Pattern.compile("[0-9]{" + (MAX_NUMBER_DIGITS + 1) + ",}");
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
        // CR, LF and TAB are separators to PathScript, but a newline in what is forwarded would become a
        // second RobotServer protocol line, so each run becomes one ';' and the caller forwards that text.
        s = LINE_BREAKS.matcher(s).replaceAll(";");
        if (s.length() > MAX_SCRIPT_CHARS) return Verdict.refuse("script is longer than " + MAX_SCRIPT_CHARS + " characters");
        // PathScript ignores what it does not recognise, so every character must belong to a term it reads.
        String bad = unrecognisedTerm(s);
        if (bad != null) return Verdict.refuse("script: unrecognised term '" + printable(bad) + "'");
        if (LONG_NUMBER.matcher(s).find()) return Verdict.refuse("script: numbers have at most " + MAX_NUMBER_DIGITS + " digits");
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

    /**
     * Scans a script with PathScript's grammar and returns the first term it would not read (from
     * the bad character's term start to the next separator), or null when every term is one it acts
     * on. Terms may run together, as the applet's appends make them ({@code z120x379}):
     * <ul>
     *   <li>separators: {@code ; , space tab CR LF};</li>
     *   <li>{@code r} (relative) before an axis {@code x y z s t};</li>
     *   <li>{@code x y z s}: an optional sign, digits, an optional decimal fraction;</li>
     *   <li>{@code t}: {@code f}, {@code v} or {@code b}, or a number as above;</li>
     *   <li>{@code g}, or a bare {@code o} or {@code c}, then any letters and digits (gopen, g0, close),
     *       which PathScript skips just as it does.</li>
     * </ul>
     */
    static String unrecognisedTerm(String s) {
        int n = s.length();
        int termStart = 0;
        for (int i = 0; i < n; ) {
            char c = s.charAt(i);
            if (SEPARATORS.indexOf(c) >= 0) {
                i++;
                termStart = i;
                continue;
            }
            int end = term(s, i);
            if (end < 0) {
                int stop = termStart;
                while (stop < n && SEPARATORS.indexOf(s.charAt(stop)) < 0) stop++;
                return s.substring(termStart, stop);
            }
            i = end;
        }
        return null;
    }

    private static final String SEPARATORS = ";, \t\r\n";

    /** The end of the term starting at {@code i}, or -1 if PathScript would not read one there. */
    private static int term(String s, int i) {
        int n = s.length();
        char c = s.charAt(i);
        if (c == 'r') {
            i++;
            if (i >= n || "xyzst".indexOf(s.charAt(i)) < 0) return -1;
            c = s.charAt(i);
        }
        switch (c) {
            case 'x', 'y', 'z', 's' -> {
                return number(s, i + 1);
            }
            case 't' -> {
                if (i + 1 < n && "fvb".indexOf(s.charAt(i + 1)) >= 0) return i + 2;
                return number(s, i + 1);
            }
            case 'g', 'o', 'c' -> {
                i++;
                while (i < n && isAsciiLetterOrDigit(s.charAt(i))) i++;
                return i;
            }
            default -> {
                return -1;
            }
        }
    }

    /** An optional sign, at least one digit, then an optional {@code .digits}; the end, or -1. */
    private static int number(String s, int i) {
        int n = s.length();
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
        int digits = i;
        while (i < n && isAsciiDigit(s.charAt(i))) i++;
        if (i == digits) return -1;
        if (i + 1 < n && s.charAt(i) == '.' && isAsciiDigit(s.charAt(i + 1))) {
            i++;
            while (i < n && isAsciiDigit(s.charAt(i))) i++;
        }
        return i;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return isAsciiDigit(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
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
