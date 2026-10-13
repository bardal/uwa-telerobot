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

class LeaseAndDeadlineTest {
    static final String ORIGIN = "https://robot.example";
    static final String SECRET = "s3cret";

    GatewayServer server;
    WebSocketClient client;

    void start(GatewayServer.Timing timing) throws Exception {
        GatewayConfig cfg = GatewayConfig.parse(new String[] {
            "--port", "0", "--allowed-origin", ORIGIN, "--origin-secret", SECRET, "--commit", "abc"}, Map.of());
        server = new GatewayServer(cfg, new StubSessionHandler(cfg), System::currentTimeMillis, timing);
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
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            while (server.sessions() != n) Thread.sleep(20);
        });
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
        awaitSessions(0);
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
        String err = r.texts.poll(5, TimeUnit.SECONDS);
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
        GatewayServerTest.Recorder r = new GatewayServerTest.Recorder();
        client.connect(r, URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req).get(5, TimeUnit.SECONDS);
        r.session.sendText("{\"t\":\"hello\",\"nick\":\"bob\"}", org.eclipse.jetty.websocket.api.Callback.NOOP);
        assertNotNull(r.texts.poll(5, TimeUnit.SECONDS));
        Thread.sleep(900);
        assertFalse(r.closed.isDone());
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
