package au.edu.uwa.mech.web;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Nicks held by gateway sessions. A wanted nick already held becomes {@code nick~2}, {@code nick~3}
 * and so on. Comparison ignores case, so "Ann" and "ann" are not both handed out. Thread-safe.
 */
public final class Nicks {
    private final Set<String> held = new HashSet<>();

    /** Holds and returns the first free one of {@code wanted}, {@code wanted~2}, {@code wanted~3}, ... */
    public synchronized String reserve(String wanted) {
        for (int n = 1; ; n++) {
            String candidate = n == 1 ? wanted : wanted + "~" + n;
            if (held.add(key(candidate))) return candidate;
        }
    }

    public synchronized void release(String nick) {
        if (nick != null) held.remove(key(nick));
    }

    public synchronized boolean held(String nick) {
        return held.contains(key(nick));
    }

    private static String key(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }
}
