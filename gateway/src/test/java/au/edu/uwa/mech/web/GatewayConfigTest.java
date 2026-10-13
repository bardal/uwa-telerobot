package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GatewayConfigTest {

    @Test
    void defaults() {
        GatewayConfig c = GatewayConfig.parse(new String[0], Map.of());
        assertEquals("127.0.0.1", c.momUrlHost());
        assertEquals(4444, c.momPort());
        assertEquals(30564, c.labAdminPort());
        assertFalse(c.dev());
        assertEquals(List.of(), c.allowedOrigins());
        assertNull(c.originSecret());
        assertEquals("dev", c.commit());
    }

    @Test
    void momPinsFromEnvAndFlags() {
        assertEquals(Map.of(), GatewayConfig.parse(new String[0], Map.of()).pins());
        Map<String, String> env = Map.of("PIN_REGISTERED", "r-env", "PIN_ADMIN", "a-env");
        assertEquals(Map.of("registered", "r-env", "admin", "a-env"), GatewayConfig.parse(new String[0], env).pins());
        GatewayConfig f = GatewayConfig.parse(new String[] {"--pin-registered", "r-flag", "--pin-admin", " "}, env);
        assertEquals(Map.of("registered", "r-flag"), f.pins(), "a blank flag clears the env value");
    }

    @Test
    void envIsReadAndFlagsOverrideIt() {
        Map<String, String> env = Map.of(
            "ALLOWED_ORIGIN", "https://a.example, https://b.example",
            "COGNITO_ISSUER", "https://issuer.example",
            "COGNITO_CLIENT_IDS", "id1,id2",
            "ARCHIVE_BUCKET", "bkt", "AWS_REGION", "eu-west-2",
            "ORIGIN_SECRET", "envsecret", "TURNSTILE_SECRET", "ts");
        GatewayConfig c = GatewayConfig.parse(new String[0], env);
        assertEquals(List.of("https://a.example", "https://b.example"), c.allowedOrigins());
        assertEquals(List.of("id1", "id2"), c.cognitoClientIds());
        assertEquals("https://issuer.example", c.cognitoIssuer());
        assertEquals("bkt", c.archiveBucket());
        assertEquals("eu-west-2", c.awsRegion());
        assertEquals("envsecret", c.originSecret());
        assertEquals("ts", c.turnstileSecret());

        GatewayConfig f = GatewayConfig.parse(new String[] {
            "--port", "9", "--mom-host", "h", "--mom-port", "5", "--lab-admin-port", "6",
            "--allowed-origin", "https://c.example", "--allowed-origin", "https://d.example",
            "--cognito-client-id", "x", "--cognito-client-id", "y", "--cognito-issuer", "https://i2",
            "--origin-secret", "flag", "--commit", "deadbeef", "--static-dir", "s", "--archive-dir", "a"}, env);
        assertEquals(9, f.port());
        assertEquals("h", f.momUrlHost());
        assertEquals(5, f.momPort());
        assertEquals(6, f.labAdminPort());
        assertEquals(List.of("https://c.example", "https://d.example"), f.allowedOrigins());
        assertEquals(List.of("x", "y"), f.cognitoClientIds());
        assertEquals("https://i2", f.cognitoIssuer());
        assertEquals("flag", f.originSecret());
        assertEquals("deadbeef", f.commit());
        assertFalse(f.dev(), "--dev with this AWS-like env is refused: see CloudConfigTest");
        assertEquals("s", f.staticDir());
        assertEquals("a", f.archiveDir());
    }

    @Test
    void devListensOnLoopbackOnlyUnlessToldOtherwise() throws Exception {
        assertEquals("127.0.0.1", GatewayConfig.parse(new String[] {"--dev"}, Map.of()).bind());
        assertEquals("0.0.0.0", GatewayConfig.parse(new String[] {"--dev", "--bind", "0.0.0.0"}, Map.of()).bind());
        assertNull(GatewayConfig.parse(new String[0], Map.of()).bind(), "production: every interface");
        GatewayConfig dev = GatewayConfig.parse(new String[] {"--dev", "--port", "0"}, Map.of());
        GatewayServer s = new GatewayServer(dev, new StubSessionHandler(dev), System::currentTimeMillis);
        s.start();
        try {
            assertEquals("127.0.0.1", s.host());
            assertEquals(200, get(s, "/healthz").statusCode());
        } finally {
            s.stop();
        }
        GatewayConfig prod = GatewayConfig.parse(new String[] {"--port", "0", "--origin-secret", "x"}, Map.of());
        GatewayServer p = new GatewayServer(prod, new StubSessionHandler(prod), System::currentTimeMillis);
        p.start();
        try {
            assertNull(p.host());
        } finally {
            p.stop();
        }
    }

    @Test
    void badArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> GatewayConfig.parse(new String[] {"--nope"}, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> GatewayConfig.parse(new String[] {"--port"}, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> GatewayConfig.parse(new String[] {"--port", "x"}, Map.of()));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> GatewayConfig.parse(new String[] {"--lab-in-process"}, Map.of()));
        assertTrue(e.getMessage().contains("--dev"), e.getMessage());
        assertTrue(GatewayConfig.parse(new String[] {"--dev", "--lab-in-process"}, Map.of()).labInProcess());
        assertFalse(GatewayConfig.parse(new String[] {"--dev"}, Map.of()).labInProcess());
    }

    @Test
    void devModeServesStaticStatusAndArchiveButProductionDoesNot(@TempDir Path tmp) throws Exception {
        Path ui = Files.createDirectories(tmp.resolve("ui"));
        Files.writeString(ui.resolve("index.html"), "<h1>hi</h1>");
        Path arch = Files.createDirectories(tmp.resolve("arch"));
        Files.createDirectories(arch.resolve("public"));
        Files.writeString(arch.resolve("public/a.txt"), "archived");

        GatewayConfig dev = GatewayConfig.parse(new String[] {"--port", "0", "--dev", "--static-dir", ui.toString(),
            "--archive-dir", arch.toString()}, Map.of());
        GatewayServer s = new GatewayServer(dev, new StubSessionHandler(dev), System::currentTimeMillis);
        s.start();
        try {
            assertEquals("<h1>hi</h1>", get(s, "/").body());
            assertEquals("<h1>hi</h1>", get(s, "/index.html").body());
            assertEquals("archived", get(s, "/archive/public/a.txt").body());
            assertEquals("{\"state\":\"running\",\"healthy\":true,\"commit\":\"dev\"}", get(s, "/api/status").body());
            assertNotEquals(200, get(s, "/archive/public/../../ui/index.html").statusCode());
        } finally {
            s.stop();
        }

        GatewayConfig prod = GatewayConfig.parse(new String[] {"--port", "0", "--origin-secret", "x",
            "--static-dir", ui.toString(), "--archive-dir", arch.toString()}, Map.of());
        GatewayServer p = new GatewayServer(prod, new StubSessionHandler(prod), System::currentTimeMillis);
        p.start();
        try {
            assertEquals(404, get(p, "/").statusCode());
            assertEquals(404, get(p, "/api/status").statusCode());
            assertEquals(404, get(p, "/archive/public/a.txt").statusCode());
            assertEquals(200, get(p, "/healthz").statusCode());
        } finally {
            p.stop();
        }
    }

    private static HttpResponse<String> get(GatewayServer s, String path) throws Exception {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + s.port() + path)).build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
