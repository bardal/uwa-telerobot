package au.edu.uwa.mech.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Session idle and age expiry, and the gateway's own instance-idle stop. Clock is in milliseconds. */
public final class Idle {
    public static final long SESSION_IDLE_MILLIS = 10 * 60_000L;
    public static final long SESSION_MAX_AGE_MILLIS = 2 * 60 * 60_000L;
    public static final long INSTANCE_IDLE_MILLIS = 15 * 60_000L;

    private static final class Entry {
        final long opened;
        long lastInput;
        Entry(long now) { opened = now; lastInput = now; }
    }

    private final LongSupplier clock;
    private final Runnable stopInstance;
    private final Map<String, Entry> sessions = new LinkedHashMap<>();
    private long lastSessionEnd;
    private boolean stopped;

    public Idle(LongSupplier clock, Runnable stopInstance) {
        this.clock = clock;
        this.stopInstance = stopInstance;
        this.lastSessionEnd = clock.getAsLong(); // a gateway nobody ever joins stops too
    }

    /** Human input (not heartbeats or activity pings) on this session. */
    public synchronized void touch(String sessionId) {
        Entry e = sessions.get(sessionId);
        if (e != null) e.lastInput = clock.getAsLong();
    }

    public synchronized void opened(String id) {
        sessions.putIfAbsent(id, new Entry(clock.getAsLong())); // re-open must not extend the 2 h cap
        stopped = false;
    }

    public synchronized void closed(String id) {
        if (sessions.remove(id) != null) lastSessionEnd = clock.getAsLong();
    }

    /** Sessions with no human input for 10 minutes, or open for 2 hours. */
    public synchronized List<String> expired() {
        long now = clock.getAsLong();
        List<String> out = new ArrayList<>();
        sessions.forEach((id, e) -> {
            if (now - e.lastInput >= SESSION_IDLE_MILLIS || now - e.opened >= SESSION_MAX_AGE_MILLIS) out.add(id);
        });
        return out;
    }

    /** Stops the instance, once, 15 minutes after the last session closed. */
    public void tick() {
        synchronized (this) {
            if (stopped || !sessions.isEmpty() || clock.getAsLong() - lastSessionEnd < INSTANCE_IDLE_MILLIS) return;
            stopped = true;
        }
        stopInstance.run();
    }

    public synchronized int sessions() {
        return sessions.size();
    }

    public synchronized long lastSessionEnd() {
        return lastSessionEnd;
    }
}
