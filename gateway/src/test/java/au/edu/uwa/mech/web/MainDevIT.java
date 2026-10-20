package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The whole gateway as Main wires it, against the in-process lab: dev mode, then production mode. */
@Tag("it")
class MainDevIT {
    static final String ORIGIN = "http://localhost";
    static final String DEV_HELLO = "{\"t\":\"hello\",\"nick\":\"tess\",\"dev\":{\"sub\":\"t\",\"groups\":[]}}";
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static LabFixture lab;
    static WebSocketClient ws;

    @TempDir Path tmp;

    @BeforeAll
    static void start() throws Exception {
        lab = LabFixture.shared();
        ws = new WebSocketClient();
        ws.start();
    }

    @AfterAll
    static void stop() throws Exception {
        if (ws != null) ws.stop();
    }

    List<String> labArgs(String... more) {
        List<String> a = new ArrayList<>(List.of("--port", "0", "--mom-port", Integer.toString(lab.momPort()),
            "--lab-admin-port", Integer.toString(lab.adminPort()), "--allowed-origin", ORIGIN,
            "--pin-registered", lab.gatewayPins().get(GatewayConfig.PIN_REGISTERED),
            "--pin-admin", lab.gatewayPins().get(GatewayConfig.PIN_ADMIN)));
        a.addAll(List.of(more));
        return a;
    }

    @Test
    void devModeWelcomesADevIdentityAndProductionRefusesIt() throws Exception {
        Path archive = tmp.resolve("archive");
        Main.Running dev = Main.start(labArgs("--dev", "--archive-dir", archive.toString()).toArray(String[]::new),
            Map.of());
        try {
            JsonNode health = json(get(dev.port(), "/healthz"));
            assertTrue(health.get("ok").asBoolean(), health.toString());
            // the probe finds the robot and the cameras in MOM within a check or two
            JsonNode[] latest = {health};
            Await.until(() -> "healthz " + latest[0], () -> {
                latest[0] = healthz(dev.port());
                return "ready".equals(latest[0].get("lab").asText());
            }, 20_000);
            assertEquals("running", json(get(dev.port(), "/api/status")).get("state").asText());

            JsonNode welcome = hello(dev.port(), null, "welcome");
            assertEquals("registered", welcome.get("level").asText(), welcome.toString());
            assertEquals(List.of("Colour", "YCam", "ZCam"),
                List.of(welcome.get("cameras").get(0).asText(), welcome.get("cameras").get(1).asText(),
                    welcome.get("cameras").get(2).asText()));

            assertEquals(204, post(dev.port(), "/dev/flush").statusCode());
            assertTrue(Files.isDirectory(archive.resolve("public")), "the dev archive lives in --archive-dir");
        } finally {
            dev.close();
        }

        Main.Running prod = Main.start(labArgs("--origin-secret", "s3cret", "--turnstile-secret", "test-secret",
            "--no-self-stop").toArray(String[]::new), Map.of());
        try {
            assertEquals(200, get(prod.port(), "/healthz").statusCode());
            JsonNode error = hello(prod.port(), "s3cret", "error");
            assertEquals("unauthorised", error.get("code").asText(), error.toString());
            assertEquals(404, get(prod.port(), "/api/status").statusCode());
            assertEquals(404, post(prod.port(), "/dev/flush").statusCode());
        } finally {
            prod.close();
        }
    }

    @Test
    void labInProcessNeedsNoOtherSettings() throws Exception {
        Path ui = Files.createDirectories(tmp.resolve("ui"));
        Files.writeString(ui.resolve("index.html"), "<h1>telerobot</h1>");
        Files.writeString(ui.resolve("csp.json"), "{\"csp\":\"default-src 'self'\"}");
        // the JVM already has a lab (LabFixture): --lab-in-process uses it rather than a second one
        Main.Running dev = Main.start(new String[] {"--dev", "--lab-in-process", "--port", "0", "--static-dir",
            ui.toString(), "--archive-dir", tmp.resolve("archive").toString()}, Map.of());
        try {
            HttpResponse<String> index = get(dev.port(), "/");
            assertEquals("<h1>telerobot</h1>", index.body());
            assertEquals("default-src 'self'", index.headers().firstValue("Content-Security-Policy").orElse(null));
            assertEquals(200, get(dev.port(), "/healthz").statusCode());
            // no --allowed-origin: the page's own origin is allowed
            JsonNode welcome = hello(dev.port(), null, "welcome", "http://localhost:" + dev.port());
            assertEquals("registered", welcome.get("level").asText(), "the lab's own PINs reach the gateway");
        } finally {
            dev.close();
        }
    }

    @Test
    void aMoveFinishingDuringShutdownIsStillWritten() throws Exception {
        Path archive = tmp.resolve("archive");
        Main.Running dev = Main.start(labArgs("--dev", "--archive-dir", archive.toString()).toArray(String[]::new),
            Map.of());
        GatewayServerTest.Recorder r = new GatewayServerTest.Recorder();
        try {
            ClientUpgradeRequest req = new ClientUpgradeRequest();
            req.setHeader("Origin", ORIGIN);
            ws.connect(r, URI.create("ws://127.0.0.1:" + dev.port() + "/ws"), req).get(5, TimeUnit.SECONDS);
            r.session.sendText("{\"t\":\"hello\",\"nick\":\"mover\",\"dev\":{\"sub\":\"mv\",\"groups\":[]}}", Callback.NOOP);
            await(r, m -> m.get("t").asText().equals("welcome"));
            r.session.sendText("{\"t\":\"takeControl\"}", Callback.NOOP);
            await(r, m -> m.get("t").asText().equals("controller") && m.get("nick").asText().equals("mover"));
            r.session.sendText("{\"t\":\"command\",\"script\":\"z110\"}", Callback.NOOP);
            await(r, m -> m.get("t").asText().equals("commandStatus") && m.get("state").asText().equals("done"));
        } finally {
            // at once: the move is still waiting for the arm to settle and for fresh frames
            dev.close();
        }
        JsonNode stopping = await(r, m -> m.get("t").asText().equals("lab"));
        assertEquals("stopping", stopping.get("state").asText());

        JsonNode days = Protocol.mapper().readTree(Files.readString(archive.resolve("public/index/days.json")));
        JsonNode day = Protocol.mapper().readTree(
            Files.readString(archive.resolve("public/index/" + days.get("days").get(0).asText() + ".json")));
        JsonNode move = null;
        for (JsonNode e : day.get("entries")) {
            if (e.get("kind").asText().equals("move") && e.get("nick").asText().equals("mover")) move = e;
        }
        assertNotNull(move, day.toString());
        assertEquals("done", move.get("result").asText(), "finished during the shutdown, then flushed: " + move);
    }

    static JsonNode await(GatewayServerTest.Recorder r, java.util.function.Predicate<JsonNode> p) throws Exception {
        long end = System.currentTimeMillis() + 30_000;
        List<String> seen = new ArrayList<>();
        while (System.currentTimeMillis() < end) {
            String m = r.texts.poll(200, TimeUnit.MILLISECONDS);
            if (m == null) continue;
            seen.add(m);
            JsonNode j = Protocol.mapper().readTree(m);
            if (p.test(j)) return j;
        }
        fail("not seen; got " + seen);
        return null;
    }

    // ---- helpers -------------------------------------------------------------------------------

    JsonNode healthz(int port) {
        try {
            return json(get(port, "/healthz"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    JsonNode hello(int port, String secret, String wanted) throws Exception {
        return hello(port, secret, wanted, ORIGIN);
    }

    /** Says the dev hello and returns the first message of type {@code wanted}. */
    JsonNode hello(int port, String secret, String wanted, String origin) throws Exception {
        GatewayServerTest.Recorder r = new GatewayServerTest.Recorder();
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        req.setHeader("Origin", origin);
        if (secret != null) req.setHeader("X-Telerobot-Origin", secret);
        ws.connect(r, URI.create("ws://127.0.0.1:" + port + "/ws"), req).get(5, TimeUnit.SECONDS);
        r.session.sendText(DEV_HELLO, Callback.NOOP);
        long end = System.currentTimeMillis() + 20_000;
        List<String> seen = new ArrayList<>();
        try {
            while (System.currentTimeMillis() < end) {
                String m = r.texts.poll(500, TimeUnit.MILLISECONDS);
                if (m == null) continue;
                seen.add(m);
                JsonNode j = Protocol.mapper().readTree(m);
                if (j.get("t").asText().equals(wanted)) return j;
            }
        } finally {
            if (r.session.isOpen()) r.session.close();
        }
        fail("no " + wanted + "; got " + seen);
        return null;
    }

    static HttpResponse<String> get(int port, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(int port, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode json(HttpResponse<String> r) throws Exception {
        assertEquals(200, r.statusCode(), r.body());
        JsonNode j = Protocol.mapper().readTree(r.body());
        assertNotNull(j);
        return j;
    }
}
