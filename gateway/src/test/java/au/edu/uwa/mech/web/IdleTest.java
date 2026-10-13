package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdleTest {
    static final long MIN = 60_000L;
    long now = 0;
    final AtomicInteger stops = new AtomicInteger();
    final Idle idle = new Idle(() -> now, stops::incrementAndGet);

    @Test
    void sessionIdleTenMinutesIsExpired() {
        idle.opened("a");
        now = 10 * MIN - 1;
        assertEquals(List.of(), idle.expired());
        now = 10 * MIN;
        assertEquals(List.of("a"), idle.expired());
    }

    @Test
    void touchResetsIdle() {
        idle.opened("a");
        now = 9 * MIN;
        idle.touch("a");
        now = 18 * MIN;
        assertEquals(List.of(), idle.expired());
        now = 19 * MIN;
        assertEquals(List.of("a"), idle.expired());
    }

    @Test
    void twoHoursOldIsExpiredEvenIfActive() {
        idle.opened("a");
        for (long t = 5 * MIN; t < 120 * MIN; t += 5 * MIN) {
            now = t;
            idle.touch("a");
            assertEquals(List.of(), idle.expired(), "at " + t);
        }
        now = 119 * MIN;
        idle.touch("a");
        now = 120 * MIN;
        assertEquals(List.of("a"), idle.expired());
    }

    @Test
    void reopeningAnIdKeepsTheOriginalOpenTime() {
        idle.opened("a");
        now = 119 * MIN;
        idle.touch("a");
        idle.opened("a");
        now = 120 * MIN;
        assertEquals(List.of("a"), idle.expired());
        assertEquals(1, idle.sessions());
    }

    @Test
    void touchOfUnknownSessionIsIgnored() {
        idle.touch("ghost");
        assertEquals(0, idle.sessions());
    }

    @Test
    void sessionsAndLastEnd() {
        now = 1000;
        idle.opened("a");
        idle.opened("b");
        assertEquals(2, idle.sessions());
        now = 2000;
        idle.closed("a");
        assertEquals(1, idle.sessions());
        now = 3000;
        idle.closed("b");
        idle.closed("b"); // idempotent
        assertEquals(0, idle.sessions());
        assertEquals(3000, idle.lastSessionEnd());
    }

    @Test
    void stopsOnceFifteenMinutesAfterLastClose() {
        now = 0;
        idle.opened("a");
        idle.closed("a");
        now = 15 * MIN - 1000; // 14:59
        idle.tick();
        assertEquals(0, stops.get());
        now = 15 * MIN;
        idle.tick();
        assertEquals(1, stops.get());
        now = 16 * MIN;
        idle.tick();
        idle.tick();
        assertEquals(1, stops.get());
    }

    @Test
    void sessionOpenedAtTenMinutesResetsTheTimer() {
        idle.opened("a");
        idle.closed("a");
        now = 10 * MIN;
        idle.opened("b");
        now = 20 * MIN;
        idle.tick();
        assertEquals(0, stops.get()); // b is still open
        now = 21 * MIN;
        idle.closed("b");
        now = 35 * MIN + 999;
        idle.tick();
        assertEquals(0, stops.get());
        now = 36 * MIN;
        idle.tick();
        assertEquals(1, stops.get());
    }

    @Test
    void neverStopsWhileASessionIsOpen() {
        idle.opened("a");
        now = 5 * 60 * MIN;
        idle.tick();
        assertEquals(0, stops.get());
    }

    @Test
    void noSessionEverStillStopsFifteenMinutesAfterStart() {
        now = 100 * MIN;
        Idle fresh = new Idle(() -> now, stops::incrementAndGet);
        now += 14 * MIN;
        fresh.tick();
        assertEquals(0, stops.get());
        now += MIN;
        fresh.tick();
        assertEquals(1, stops.get());
    }
}
