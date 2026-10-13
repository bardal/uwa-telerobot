package au.edu.uwa.mech.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat word filter: case-insensitive, whole-word (or whole-phrase) matching after NFKC folding.
 * Deliberately simple; deliberate evasion ("sp4m") is out of scope.
 */
public final class Blocklist {
    private static final Blocklist DEFAULT = load("/blocklist.txt");

    private final Pattern pattern;

    public Blocklist(List<String> entries) {
        List<String> alts = new ArrayList<>();
        for (String e : entries) {
            String w = fold(e);
            if (w.isEmpty()) continue;
            alts.add(Pattern.quote(w).replace(" ", "\\E\\s+\\Q"));
        }
        pattern = alts.isEmpty() ? null : Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:" + String.join("|", alts) + ")(?![\\p{L}\\p{N}])");
    }

    public static Blocklist defaults() {
        return DEFAULT;
    }

    static Blocklist load(String resource) {
        try (InputStream in = Blocklist.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing resource " + resource);
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.strip();
                    if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
                }
            }
            return new Blocklist(lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
    }

    public boolean blocks(String text) {
        if (pattern == null) return false;
        Matcher m = pattern.matcher(fold(text));
        return m.find();
    }
}
