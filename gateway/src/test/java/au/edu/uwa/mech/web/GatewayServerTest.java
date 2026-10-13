package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GatewayServerTest {
    static final String ORIGIN = "https://robot.example";
    static final String SECRET = "s3cret";

    GatewayServer server;
    WebSocketClient client;

    @BeforeEach
    void start() throws Exception {
        GatewayConfig cfg = GatewayConfig.parse(new String[] {
            "--port", "0", "--allowed-origin", ORIGIN, "--origin-secret", SECRET, "--commit", "abc123"}, Map.of());
        server = new GatewayServer(cfg, new StubSessionHandler(cfg), System::currentTimeMillis);
        server.start();
        client = new WebSocketClient();
        client.start();
    }

    @AfterEach
    void stop() throws Exception {
        client.stop();
        server.stop();
    }

    /** Collects what the server sends. */
    public static final class Recorder implements Session.Listener.AutoDemanding {
        final BlockingQueue<String> texts = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        volatile Session session;

        @Override public void onWebSocketOpen(Session s) { session = s; }
        @Override public void onWebSocketText(String m) { texts.add(m); }
        @Override public void onWebSocketClose(int code, String reason) { closed.complete(code); }
    }

    Recorder connect(String origin, String secret) throws Exception {
        Recorder r = new Recorder();
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        if (origin != null) req.setHeader("Origin", origin);
        if (secret != null) req.setHeader("X-Telerobot-Origin", secret);
        client.connect(r, URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req).get(5, TimeUnit.SECONDS);
        return r;
    }

    @Test
    void healthzReportsCommit() throws Exception {
        HttpResponse<String> resp = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/healthz")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertEquals("{\"ok\":true,\"sessions\":0,\"commit\":\"abc123\",\"lab\":\"ready\"}", resp.body());
    }

    @Test
    void rightOriginAndSecretConnectsAndHelloGetsWelcome() throws Exception {
        Recorder r = connect(ORIGIN, SECRET);
        r.session.sendText("{\"t\":\"hello\",\"nick\":\"bob\"}", Callback.NOOP);
        String welcome = r.texts.poll(5, TimeUnit.SECONDS);
        assertNotNull(welcome);
        assertTrue(welcome.startsWith("{\"t\":\"welcome\",\"nick\":\"bob\",\"level\":\"guest\""), welcome);
        assertTrue(welcome.contains("\"commit\":\"abc123\""), welcome);
        assertEquals(1, server.sessions());
        r.session.close();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> { while (server.sessions() != 0) Thread.sleep(20); });
    }

    @Test
    void wrongOriginIsRefusedWith403() {
        assertForbidden(() -> connect("https://evil.example", SECRET));
    }

    @Test
    void wrongOrMissingSecretIsRefusedWith403() {
        assertForbidden(() -> connect(ORIGIN, "nope"));
        assertForbidden(() -> connect(ORIGIN, null));
    }

    interface Connecting { void run() throws Exception; }

    private static void assertForbidden(Connecting e) {
        ExecutionException ex = assertThrows(ExecutionException.class, e::run);
        Throwable c = ex.getCause();
        assertTrue(c instanceof org.eclipse.jetty.websocket.api.exceptions.UpgradeException, String.valueOf(c));
        assertEquals(403, ((org.eclipse.jetty.websocket.api.exceptions.UpgradeException) c).getResponseStatusCode());
    }

    @Test
    void binaryFrameClosesWith1003() throws Exception {
        Recorder r = connect(ORIGIN, SECRET);
        r.session.sendText("{\"t\":\"hello\",\"nick\":\"bob\"}", Callback.NOOP);
        assertNotNull(r.texts.poll(5, TimeUnit.SECONDS));
        r.session.sendBinary(ByteBuffer.wrap(new byte[] {1, 2, 3}), Callback.NOOP);
        assertEquals(1003, r.closed.get(5, TimeUnit.SECONDS));
    }

    @Test
    void binaryFrameBeforeHelloClosesWith1003() throws Exception {
        Recorder r = connect(ORIGIN, SECRET);
        r.session.sendBinary(ByteBuffer.wrap(new byte[] {(byte) 0xAC, (byte) 0xED, 0, 5}), Callback.NOOP);
        assertEquals(1003, r.closed.get(5, TimeUnit.SECONDS));
    }

    @Test
    void firstMessageThatIsNotHelloGetsUnauthorisedAndCloses() throws Exception {
        Recorder r = connect(ORIGIN, SECRET);
        r.session.sendText("{\"t\":\"command\",\"script\":\"x\"}", Callback.NOOP);
        String err = r.texts.poll(5, TimeUnit.SECONDS);
        assertTrue(err.startsWith("{\"t\":\"error\",\"code\":\"unauthorised\""), err);
        r.closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    void garbageFirstMessageGetsUnauthorisedAndCloses() throws Exception {
        Recorder r = connect(ORIGIN, SECRET);
        r.session.sendText("not json at all", Callback.NOOP);
        String err = r.texts.poll(5, TimeUnit.SECONDS);
        assertTrue(err.startsWith("{\"t\":\"error\",\"code\":\"unauthorised\""), err);
        r.closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    void malformedMessageAfterHelloGetsBadMessageAndStaysOpen() throws Exception {
        Recorder r = connect(ORIGIN, SECRET);
        r.session.sendText("{\"t\":\"hello\",\"nick\":\"bob\"}", Callback.NOOP);
        assertNotNull(r.texts.poll(5, TimeUnit.SECONDS));
        r.session.sendText("{\"t\":\"command\",\"script\":5}", Callback.NOOP);
        String err = r.texts.poll(5, TimeUnit.SECONDS);
        assertTrue(err.startsWith("{\"t\":\"error\",\"code\":\"bad-message\""), err);
        assertFalse(r.closed.isDone());
    }

    @Test
    void oversizeTextFrameClosesTheSession() throws Exception {
        Recorder r = connect(ORIGIN, SECRET);
        r.session.sendText("{\"t\":\"hello\",\"nick\":\"" + "a".repeat(5000) + "\"}", Callback.NOOP);
        assertEquals(1009, r.closed.get(5, TimeUnit.SECONDS));
    }

    @Test
    void fifthConnectionFromOneIpIsRefused() throws Exception {
        for (int i = 0; i < 4; i++) connect(ORIGIN, SECRET);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> connect(ORIGIN, SECRET));
        assertEquals(429, ((org.eclipse.jetty.websocket.api.exceptions.UpgradeException) ex.getCause()).getResponseStatusCode());
    }
}
