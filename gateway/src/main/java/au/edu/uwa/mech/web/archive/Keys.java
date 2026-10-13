package au.edu.uwa.mech.web.archive;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/** The only place archive keys are built. Every key is lowercase and made of [a-z0-9/_.-]. */
public final class Keys {
    public static final String DAYS = "public/index/days.json";
    static final Pattern ID = Pattern.compile("^(\\d{1,15})-[a-z0-9]{4}$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    private Keys() {}

    /** UTC day, e.g. 2026-10-13. */
    public static String day(long epochMillis) {
        return DAY.format(Instant.ofEpochMilli(epochMillis));
    }

    /** The camera name lowercased and filtered to [a-z0-9]; empty if nothing is left. */
    public static String camera(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The epoch millis encoded in an id, or -1 if the id is malformed. */
    public static long idTime(String id) {
        if (id == null) return -1;
        var m = ID.matcher(id);
        return m.matches() ? Long.parseLong(m.group(1)) : -1;
    }

    /** phase is "before" or "after". */
    public static String moveImage(String id, long time, String camera, String phase) {
        return "public/moves/" + PATH.format(Instant.ofEpochMilli(time)) + "/" + id + "-" + camera + "-" + phase + ".jpg";
    }

    public static String snapshotImage(String id, long time, String camera) {
        return "public/snapshots/" + PATH.format(Instant.ofEpochMilli(time)) + "/" + id + "-" + camera + ".jpg";
    }

    public static String dayIndex(String day) {
        return "public/index/" + day + ".json";
    }

    public static String audit(String day) {
        return "private/audit/" + day + ".jsonl";
    }
}
