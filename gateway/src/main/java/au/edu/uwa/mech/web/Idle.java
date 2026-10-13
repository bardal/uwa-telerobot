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
    /** What a session closed by {@link #expiredWithReasons()} is told. */
    public static final String IDLE_MESSAGE = "Closed after 10 minutes without input";
    public static final String MAX_AGE_MESSAGE = "Closed after 2 hours; reconnect to carry on";

    private static final class Entry {
        final long opened;
        long lastInput;
        Entry(long now) { opened = now; lastInput = now; }
    }

    private final LongSupplier clock;
    private final Runnable stopInstance;
    private final Map<String, Entry> sessions = new LinkedHashMap<>();
    private long lastSessionEnd;
    private boolean anyEnded;
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
        if (sessions.remove(id) != null) {
            lastSessionEnd = clock.getAsLong();
            anyEnded = true;
        }
    }

    /** Sessions with no human input for 10 minutes, or open for 2 hours. */
    public synchronized List<String> expired() {
        return new ArrayList<>(expiredWithReasons().keySet());
    }

    /** Like {@link #expired()}, with the message for each: {@link #MAX_AGE_MESSAGE} or {@link #IDLE_MESSAGE}. */
    public synchronized Map<String, String> expiredWithReasons() {
        long now = clock.getAsLong();
        Map<String, String> out = new LinkedHashMap<>();
        sessions.forEach((id, e) -> {
            if (now - e.opened >= SESSION_MAX_AGE_MILLIS) out.put(id, MAX_AGE_MESSAGE);
            else if (now - e.lastInput >= SESSION_IDLE_MILLIS) out.put(id, IDLE_MESSAGE);
        });
        return out;
    }

    /**
     * Stops the instance, once, 15 minutes after the last session closed. If the stop throws, the
     * next tick tries again.
     */
    public void tick() {
        synchronized (this) {
            if (stopped || !sessions.isEmpty() || clock.getAsLong() - lastSessionEnd < INSTANCE_IDLE_MILLIS) return;
            stopped = true;
        }
        try {
            stopInstance.run();
        } catch (RuntimeException e) {
            synchronized (this) {
                stopped = false;
            }
            throw e;
        }
    }

    public synchronized int sessions() {
        return sessions.size();
    }

    /** When the idle timer started: the last session's end, or construction if none has ended. */
    public synchronized long lastSessionEnd() {
        return lastSessionEnd;
    }

    /** When the last session ended, or 0 if none has (the heartbeat's {@code lastSessionEnd}). */
    public synchronized long endedAt() {
        return anyEnded ? lastSessionEnd : 0;
    }
}
