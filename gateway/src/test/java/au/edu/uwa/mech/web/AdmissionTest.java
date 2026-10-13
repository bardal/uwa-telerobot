package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AdmissionTest {
    static final String SECRET = "s3cret";
    final AtomicLong now = new AtomicLong(1_000_000);

    static GatewayConfig config(boolean dev, String secret) {
        List<String> args = new ArrayList<>(List.of("--allowed-origin", "https://robot.example", "--allowed-origin", "https://b.example"));
        if (dev) args.add("--dev");
        if (secret != null) { args.add("--origin-secret"); args.add(secret); }
        return GatewayConfig.parse(args.toArray(new String[0]), Map.of());
    }

    static Function<String, String> headers(String... kv) {
        Map<String, String> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m::get;
    }

    Admission admission(boolean dev, String secret) {
        return new Admission(config(dev, secret), now::get);
    }

    static Function<String, String> good() {
        return headers("Origin", "https://robot.example", "X-Telerobot-Origin", SECRET);
    }

    @Test
    void validRequestIsAdmitted() {
        assertEquals(Optional.empty(), admission(false, SECRET).check(good(), "10.0.0.1"));
    }

    @Test
    void wrongOrMissingSecretRefusedOutsideDev() {
        Admission a = admission(false, SECRET);
        assertTrue(a.check(headers("Origin", "https://robot.example"), "10.0.0.1").isPresent());
        now.addAndGet(1000);
        assertTrue(a.check(headers("Origin", "https://robot.example", "X-Telerobot-Origin", "nope"), "10.0.0.1").isPresent());
        now.addAndGet(1000);
        assertTrue(a.check(headers("Origin", "https://robot.example", "X-Telerobot-Origin", "s3cret2"), "10.0.0.1").isPresent());
    }

    @Test
    void productionWithNoConfiguredSecretRefusesEverything() {
        Admission a = admission(false, null);
        assertTrue(a.check(headers("Origin", "https://robot.example"), "10.0.0.1").isPresent());
        assertTrue(a.check(headers("Origin", "https://robot.example", "X-Telerobot-Origin", ""), "10.0.0.1").isPresent());
    }

    @Test
    void devWithoutSecretSkipsHeaderButStillChecksOrigin() {
        Admission a = admission(true, null);
        assertTrue(a.check(headers("Origin", "https://robot.example"), "10.0.0.1").isEmpty());
        assertTrue(a.check(headers("Origin", "https://evil.example"), "10.0.0.1").isPresent());
    }

    @Test
    void originNotInListRefusedOrMissing() {
        Admission a = admission(false, SECRET);
        assertTrue(a.check(headers("Origin", "https://evil.example", "X-Telerobot-Origin", SECRET), "10.0.0.1").isPresent());
        assertTrue(a.check(headers("Origin", "https://robot.example.evil", "X-Telerobot-Origin", SECRET), "10.0.0.1").isPresent());
        assertTrue(a.check(headers("X-Telerobot-Origin", SECRET), "10.0.0.1").isPresent());
        assertTrue(a.check(headers("Origin", "https://b.example", "X-Telerobot-Origin", SECRET), "10.0.0.1").isEmpty());
    }

    @Test
    void fifthConnectionFromTheSameIpRefused() {
        Admission a = admission(false, SECRET);
        for (int i = 0; i < 4; i++) { assertTrue(a.check(good(), "10.0.0.1").isEmpty(), "conn " + i); now.addAndGet(1000); }
        assertTrue(a.check(good(), "10.0.0.1").isPresent());
        assertTrue(a.check(good(), "10.0.0.2").isEmpty());
        a.release("10.0.0.1");
        now.addAndGet(1000);
        assertTrue(a.check(good(), "10.0.0.1").isEmpty());
    }

    @Test
    void sixtyFirstViewerRefused() {
        Admission a = admission(false, SECRET);
        for (int i = 0; i < 60; i++) {
            assertTrue(a.check(good(), "10.1." + (i / 200) + "." + i).isEmpty(), "viewer " + i);
            now.addAndGet(200);
        }
        assertEquals(60, a.viewers());
        assertTrue(a.check(good(), "10.9.9.9").isPresent());
        a.release("10.1.0.0");
        assertTrue(a.check(good(), "10.9.9.9").isEmpty());
    }

    @Test
    void eleventhHandshakeWithinOneSecondRefused() {
        Admission a = admission(false, SECRET);
        for (int i = 0; i < 10; i++) assertTrue(a.check(good(), "10.2.0." + i).isEmpty(), "hs " + i);
        assertTrue(a.check(good(), "10.2.0.50").isPresent());
        now.addAndGet(1001);
        assertTrue(a.check(good(), "10.2.0.51").isEmpty());
    }

    @Test
    void clientIpComesFromCloudFrontOnlyWhenSecretIsValid() {
        Admission a = admission(false, SECRET);
        assertEquals("203.0.113.9", a.resolveIp(headers("X-Telerobot-Origin", SECRET, "CloudFront-Viewer-Address", "203.0.113.9:5555"), "10.0.0.1"));
        assertEquals("2001:db8::1", a.resolveIp(headers("X-Telerobot-Origin", SECRET, "CloudFront-Viewer-Address", "[2001:db8::1]:443"), "10.0.0.1"));
        assertEquals("2001:db8::1", a.resolveIp(headers("X-Telerobot-Origin", SECRET, "CloudFront-Viewer-Address", "2001:db8::1:443"), "10.0.0.1"));
        assertEquals("10.0.0.1", a.resolveIp(headers("X-Telerobot-Origin", "bad", "CloudFront-Viewer-Address", "203.0.113.9:5555"), "10.0.0.1"));
        assertEquals("10.0.0.1", a.resolveIp(headers("CloudFront-Viewer-Address", "203.0.113.9:5555"), "10.0.0.1"));
        assertEquals("10.0.0.1", a.resolveIp(headers("X-Telerobot-Origin", SECRET, "CloudFront-Viewer-Address", "garbage<script>:1"), "10.0.0.1"));
        assertEquals("10.0.0.1", a.resolveIp(headers("X-Telerobot-Origin", SECRET), "10.0.0.1"));
    }

    @Test
    void perIpCapUsesTheCloudFrontAddress() {
        Admission a = admission(false, SECRET);
        var h = headers("Origin", "https://robot.example", "X-Telerobot-Origin", SECRET, "CloudFront-Viewer-Address", "203.0.113.9:1");
        for (int i = 0; i < 4; i++) { assertTrue(a.check(h, "10.0.0." + i).isEmpty()); now.addAndGet(1000); }
        assertTrue(a.check(h, "10.0.0.9").isPresent());
    }
}
