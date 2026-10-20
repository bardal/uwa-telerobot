package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Leases, the hello deadline and pings, on a {@link ManualTime}: the test moves the clock. */
class LeaseAndDeadlineTest {
    static final String ORIGIN = "https://robot.example";
    static final String SECRET = "s3cret";

    final ManualTime time = new ManualTime();
    GatewayServer server;
    WebSocketClient client;

    void start(GatewayServer.Timing timing) throws Exception {
        GatewayConfig cfg = GatewayConfig.parse(new String[] {
            "--port", "0", "--allowed-origin", ORIGIN, "--origin-secret", SECRET, "--commit", "abc"}, Map.of());
        server = new GatewayServer(cfg, new StubSessionHandler(cfg), time, timing, time.scheduler());
        server.start();
        client = new WebSocketClient();
        client.start();
    }

    @AfterEach
    void stop() throws Exception {
        if (client != null) client.stop();
        if (server != null) server.stop();
    }

    private void awaitSessions(int n) {
        Await.until(() -> "sessions " + server.sessions() + ", wanted " + n, () -> server.sessions() == n, 10_000);
    }

    @Test
    void clientThatAbortsDuringTheUpgradeDoesNotLeakItsSlot() throws Exception {
        start(new GatewayServer.Timing(300, 15_000, 50));
        for (int i = 0; i < 3; i++) {
            try (Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                s.setSoLinger(true, 0); // RST on close
                String req = "GET /ws HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n"
                    + "Origin: " + ORIGIN + "\r\nX-Telerobot-Origin: " + SECRET + "\r\n\r\n";
                OutputStream out = s.getOutputStream();
                out.write(req.getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } // aborted before reading the 101
        }
        // each abandoned upgrade holds its slot until its lease runs out and a sweep frees it; the server
        // takes them on its own threads, so keep moving the clock until every one has been swept
        Await.until(() -> "sessions " + server.sessions(), () -> {
            time.advance(350);
            return server.sessions() == 0;
        }, 10_000);
        // and the caps are intact: a normal client still connects
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        req.setHeader("Origin", ORIGIN);
        req.setHeader("X-Telerobot-Origin", SECRET);
        client.connect(new GatewayServerTest.Recorder(), URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req)
            .get(5, TimeUnit.SECONDS);
        awaitSessions(1);
    }

    @Test
    void connectionWithoutHelloIsClosedAfterTheDeadlineAndItsSlotFreed() throws Exception {
        start(new GatewayServer.Timing(10_000, 300, 1_000));
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        req.setHeader("Origin", ORIGIN);
        req.setHeader("X-Telerobot-Origin", SECRET);
        GatewayServerTest.Recorder r = new GatewayServerTest.Recorder();
        client.connect(r, URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req).get(5, TimeUnit.SECONDS);
        // the deadline is set when the server opens the socket, on its own thread: move the clock past it
        // until it has gone off
        Await.until("the deadline closed the socket", () -> {
            time.advance(300);
            return !r.texts.isEmpty();
        }, 10_000);
        String err = r.texts.poll();
        assertNotNull(err);
        assertTrue(err.startsWith("{\"t\":\"error\",\"code\":\"unauthorised\""), err);
        assertEquals(1008, r.closed.get(5, TimeUnit.SECONDS));
        awaitSessions(0);
    }

    @Test
    void helloInTimeCancelsTheDeadline() throws Exception {
        start(new GatewayServer.Timing(10_000, 400, 1_000));
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        req.setHeader("Origin", ORIGIN);
        req.setHeader("X-Telerobot-Origin", SECRET);
        PongRecorder r = new PongRecorder();
        client.connect(r, URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req).get(5, TimeUnit.SECONDS);
        r.session.sendText("{\"t\":\"hello\",\"nick\":\"bob\"}", org.eclipse.jetty.websocket.api.Callback.NOOP);
        assertNotNull(r.texts.poll(5, TimeUnit.SECONDS)); // the welcome: the server has seen the hello
        time.advance(1_000); // well past the deadline: it runs now, on this thread
        // a ping's pong comes back behind anything the deadline sent, so by then a close would be here
        r.session.sendPing(java.nio.ByteBuffer.allocate(0), org.eclipse.jetty.websocket.api.Callback.NOOP);
        r.pong.get(5, TimeUnit.SECONDS);
        assertFalse(r.closed.isDone());
        assertEquals(null, r.texts.poll(), "no error after the welcome");
    }

    /** Notes the first pong. */
    public static class PongRecorder extends GatewayServerTest.Recorder {
        final java.util.concurrent.CompletableFuture<Boolean> pong = new java.util.concurrent.CompletableFuture<>();

        @Override public void onWebSocketPong(java.nio.ByteBuffer payload) {
            pong.complete(true);
        }
    }

    /** Counts the server's pings and answers each with a pong, as browsers do. */
    public static class PingRecorder extends GatewayServerTest.Recorder {
        final java.util.concurrent.atomic.AtomicInteger pings = new java.util.concurrent.atomic.AtomicInteger();

        @Override public void onWebSocketPing(java.nio.ByteBuffer payload) {
            pings.incrementAndGet();
            session.sendPong(payload, org.eclipse.jetty.websocket.api.Callback.NOOP);
        }
    }

    @Test
    void everyOpenSocketIsPingedOnTheIntervalAndThePongsAreHarmless() throws Exception {
        start(new GatewayServer.Timing(10_000, 15_000, 1_000, 150));
        PingRecorder r = new PingRecorder();
        java.util.concurrent.atomic.AtomicInteger pings = r.pings;
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        req.setHeader("Origin", ORIGIN);
        req.setHeader("X-Telerobot-Origin", SECRET);
        client.connect(r, URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req).get(5, TimeUnit.SECONDS);
        r.session.sendText("{\"t\":\"hello\",\"nick\":\"bob\"}", org.eclipse.jetty.websocket.api.Callback.NOOP);
        assertNotNull(r.texts.poll(5, TimeUnit.SECONDS)); // the welcome: the socket is open, its pings are set
        for (int i = 0; i < 4; i++) time.advance(150);
        Await.until(() -> "pings " + pings.get(), () -> pings.get() == 4, 10_000);
        assertFalse(r.closed.isDone(), "pongs do not upset the session");
        assertEquals(1, server.sessions());
        r.session.close();
        awaitSessions(0);
        assertEquals(1, time.pending(), "only the admission sweep is left: the socket's pings are cancelled");
        time.advance(500);
        assertEquals(4, pings.get(), "no pings once the socket is closed");
    }

    @Test
    void theDefaultPingIntervalIsThirtySeconds() {
        assertEquals(30_000, GatewayServer.Timing.DEFAULT.pingMillis());
    }

    @Test
    void refusalBodiesNameNoCheck() throws Exception {
        start(GatewayServer.Timing.DEFAULT);
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            s.getOutputStream().write(("GET /ws HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n"
                + "Origin: https://evil.example\r\nX-Telerobot-Origin: wrong\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            String resp = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).toLowerCase();
            assertTrue(resp.startsWith("http/1.1 403"), resp);
            assertFalse(resp.contains("bad-secret") || resp.contains("bad-origin") || resp.contains("secret"), resp);
        }
    }
}
