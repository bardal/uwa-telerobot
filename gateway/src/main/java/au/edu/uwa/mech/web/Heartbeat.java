package au.edu.uwa.mech.web;

import au.edu.uwa.mech.web.cloud.ParamStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the reaper the gateway is alive: {@code {"time","sessions","lastSessionEnd","commit"}} in
 * {@value #PARAM}, at start and then every 60 s. Call {@link #tick()} often (Main does every 5 s);
 * it writes when one is due, and a failed write is retried on the next tick.
 */
public final class Heartbeat {
    private static final Logger LOG = LoggerFactory.getLogger(Heartbeat.class);
    public static final String PARAM = "/telerobot/heartbeat";
    public static final long INTERVAL_MILLIS = 60_000;

    private final ParamStore store;
    private final LongSupplier clock;
    private final IntSupplier sessions;
    private final LongSupplier lastSessionEnd;
    private final String commit;
    private long lastWrite;
    private boolean written;

    public Heartbeat(ParamStore store, LongSupplier clock, IntSupplier sessions, LongSupplier lastSessionEnd, String commit) {
        this.store = store;
        this.clock = clock;
        this.sessions = sessions;
        this.lastSessionEnd = lastSessionEnd;
        this.commit = commit;
    }

    /** Writes the heartbeat if none has been written yet or the last is 60 s old. True if it wrote. */
    public synchronized boolean tick() {
        long now = clock.getAsLong();
        if (written && now - lastWrite < INTERVAL_MILLIS) return false;
        try {
            store.put(PARAM, json(now));
        } catch (RuntimeException e) {
            LOG.warn("heartbeat write failed: {}", e.toString());
            return false;
        }
        written = true;
        lastWrite = now;
        return true;
    }

    String json(long now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", now);
        m.put("sessions", sessions.getAsInt());
        m.put("lastSessionEnd", lastSessionEnd.getAsLong());
        m.put("commit", commit);
        return Out.json(m);
    }
}
