package au.edu.uwa.mech.web;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Banned subjects and IP hashes, persisted as a JSON array of strings in the {@link ParamStore}
 * parameter {@value #PARAM}. Banning an identity bans both its sub and its ipHash. Thread-safe.
 */
public final class Bans {
    private static final Logger LOG = LoggerFactory.getLogger(Bans.class);
    public static final String PARAM = "/telerobot/bans";

    private final ParamStore store;
    private final Set<String> banned = new TreeSet<>();

    public Bans(ParamStore store) {
        this.store = store;
        store.get(PARAM).ifPresent(json -> {
            try {
                banned.addAll(Protocol.mapper().readValue(json, new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                // an unreadable list must not stop the gateway; it is rewritten on the next ban
                LOG.warn("ignoring unreadable {}: {}", PARAM, e.getClass().getSimpleName());
            }
        });
    }

    public synchronized boolean banned(Identity id) {
        return banned.contains(id.sub()) || (id.ipHash() != null && banned.contains(id.ipHash()));
    }

    public synchronized void ban(Identity id) {
        banned.add(id.sub());
        if (id.ipHash() != null) banned.add(id.ipHash());
        store.put(PARAM, Out.json(List.copyOf(banned)));
    }

    public synchronized Set<String> all() {
        return Set.copyOf(banned);
    }
}
