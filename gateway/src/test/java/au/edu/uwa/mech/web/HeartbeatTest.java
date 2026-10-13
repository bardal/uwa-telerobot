package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import au.edu.uwa.mech.web.cloud.MemoryParamStore;
import au.edu.uwa.mech.web.cloud.ParamStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HeartbeatTest {
    long now = 1_760_000_000_000L;
    int sessions = 2;
    long lastEnd = 0;

    @Test
    void writesTheFourFieldsAtStartThenEverySixtySeconds() throws Exception {
        MemoryParamStore store = new MemoryParamStore();
        List<String> writes = new ArrayList<>();
        ParamStore recording = new ParamStore() {
            @Override public Optional<String> get(String name) { return store.get(name); }
            @Override public void put(String name, String value) {
                assertEquals("/telerobot/heartbeat", name);
                writes.add(value);
                store.put(name, value);
            }
        };
        Heartbeat hb = new Heartbeat(recording, () -> now, () -> sessions, () -> lastEnd, "abc1234");

        assertTrue(hb.tick(), "the first tick (at start) always writes");
        assertEquals("{\"time\":" + now + ",\"sessions\":2,\"lastSessionEnd\":0,\"commit\":\"abc1234\"}",
            store.get(Heartbeat.PARAM).orElseThrow());

        long start = now;
        now = start + 59_999;
        assertFalse(hb.tick());
        sessions = 0;
        lastEnd = start + 30_000;
        now = start + 60_000;
        assertTrue(hb.tick());
        JsonNode j = Protocol.mapper().readTree(store.get(Heartbeat.PARAM).orElseThrow());
        assertEquals(now, j.get("time").asLong());
        assertEquals(0, j.get("sessions").asInt());
        assertEquals(start + 30_000, j.get("lastSessionEnd").asLong());
        assertEquals("abc1234", j.get("commit").asText());
        assertEquals(4, j.size(), "exactly time, sessions, lastSessionEnd and commit");

        now = start + 119_999;
        assertFalse(hb.tick());
        now = start + 120_000;
        assertTrue(hb.tick());
        assertEquals(3, writes.size());
    }

    @Test
    void aFailedWriteIsRetriedOnTheNextTick() {
        List<String> writes = new ArrayList<>();
        boolean[] fail = {true};
        ParamStore flaky = new ParamStore() {
            @Override public Optional<String> get(String name) { return Optional.empty(); }
            @Override public void put(String name, String value) {
                if (fail[0]) throw new IllegalStateException("ssm down");
                writes.add(value);
            }
        };
        Heartbeat hb = new Heartbeat(flaky, () -> now, () -> sessions, () -> lastEnd, "c");
        assertFalse(hb.tick(), "a failure is logged, not thrown");
        fail[0] = false;
        now += 5_000;
        assertTrue(hb.tick(), "not 60 s later: straight away");
        assertEquals(1, writes.size());
    }
}
