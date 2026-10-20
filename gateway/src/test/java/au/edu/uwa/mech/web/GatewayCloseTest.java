package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A close we start: later frames are ignored, and written-callbacks and the backlog are real. */
class GatewayCloseTest {
    static final String ORIGIN = "https://robot.example";
    static final String SECRET = "s3cret";

    final AtomicInteger messages = new AtomicInteger();
    final AtomicInteger closes = new AtomicInteger();
    final CountDownLatch written = new CountDownLatch(1);
    final AtomicReference<ClientConnection> conn = new AtomicReference<>();
    GatewayServer server;
    WebSocketClient client;

    @BeforeEach
    void start() throws Exception {
        GatewayConfig cfg = GatewayConfig.parse(new String[] {
            "--port", "0", "--allowed-origin", ORIGIN, "--origin-secret", SECRET}, Map.of());
        server = new GatewayServer(cfg, new SessionHandler() {
            @Override
            public void onHello(ClientConnection c, In.Hello hello) {
                conn.set(c);
                c.send(new Out.Welcome(hello.nick(), "guest", List.of(), "x", new Out.Limits(400, 30, 300)),
                    written::countDown);
            }

            @Override
            public void onMessage(ClientConnection c, In message) {
                messages.incrementAndGet();
                c.close(1008, "kicked"); // the first message gets the session closed
            }

            @Override
            public void onClose(ClientConnection c) {
                closes.incrementAndGet();
            }
        }, System::currentTimeMillis);
        server.start();
        client = new WebSocketClient();
        client.start();
    }

    @AfterEach
    void stop() throws Exception {
        client.stop();
        server.stop();
    }

    @Test
    void framesAfterOurCloseAreIgnoredAndCallbacksRun() throws Exception {
        GatewayServerTest.Recorder r = new GatewayServerTest.Recorder();
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        req.setHeader("Origin", ORIGIN);
        req.setHeader("X-Telerobot-Origin", SECRET);
        client.connect(r, URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req).get(5, TimeUnit.SECONDS);
        r.session.sendText("{\"t\":\"hello\",\"nick\":\"bob\"}", Callback.NOOP);
        assertNotNull(r.texts.poll(5, TimeUnit.SECONDS));
        assertTrue(written.await(5, TimeUnit.SECONDS), "the welcome's written callback ran");
        assertEquals(0, conn.get().queuedBytes());
        assertEquals(0, conn.get().queuedMessages());

        for (int i = 0; i < 50; i++) r.session.sendText("{\"t\":\"activity\"}", Callback.NOOP);
        // Wait for the server to finish with the socket, not for the client to see the close: the client is
        // still writing, and its view of the close is a race with the TCP reset (it once missed it under
        // load). Once onClose has run the connection is over, so nothing more can reach the handler.
        Await.until(() -> "closes " + closes.get(), () -> closes.get() == 1, 10_000);
        assertEquals(1, messages.get(), "nothing after our close reaches the handler");
        assertTrue(!conn.get().isOpen());
        assertEquals(1, closes.get(), "onClose once");
    }
}
