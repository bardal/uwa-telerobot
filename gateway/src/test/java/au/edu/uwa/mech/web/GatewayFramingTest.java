package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Frame;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What iPhone Safari gets: no permessage-deflate, and a camera JPEG bigger than the 4 KB inbound
 * frame limit arrives as one whole frame (Safari closed with 1002 on compressed 4 KB fragments).
 */
class GatewayFramingTest {
    static final String ORIGIN = "https://robot.example";
    static final String SECRET = "s3cret";
    static final byte[] JPEG = new byte[30_000];

    GatewayServer server;
    WebSocketClient client;

    @BeforeEach
    void start() throws Exception {
        for (int i = 0; i < JPEG.length; i++) JPEG[i] = (byte) (i * 31 + (i >> 7));
        GatewayConfig cfg = GatewayConfig.parse(new String[] {
            "--port", "0", "--allowed-origin", ORIGIN, "--origin-secret", SECRET}, Map.of());
        server = new GatewayServer(cfg, new SessionHandler() {
            @Override
            public void onHello(ClientConnection c, In.Hello hello) {
                c.sendImage(new Out.Image("Colour", 1, 400, 300, java.util.Collections.nCopies(12, 1.0)), JPEG, () -> { });
            }

            @Override public void onMessage(ClientConnection c, In message) { }

            @Override public void onClose(ClientConnection c) { }
        }, System::currentTimeMillis);
        server.start();
        client = new WebSocketClient();
        client.setInputBufferSize(256 * 1024); // so it reports the frames as sent, not cut to its buffer
        client.setMaxFrameSize(256 * 1024);
        client.start();
    }

    @AfterEach
    void stop() throws Exception {
        client.stop();
        server.stop();
    }

    /** Records every data frame as it arrives, before any reassembly. */
    public static final class Frames implements Session.Listener.AutoDemanding {
        final List<String> frames = new CopyOnWriteArrayList<>();
        final CompletableFuture<byte[]> binary = new CompletableFuture<>();
        volatile Session session;

        @Override public void onWebSocketOpen(Session s) { session = s; }

        @Override
        public void onWebSocketFrame(Frame frame, Callback callback) {
            if (frame.getOpCode() <= 2) {
                frames.add(frame.getOpCode() + (frame.isFin() ? "F" : "-") + (frame.isRsv1() ? "C" : "") + ":" + frame.getPayloadLength());
            }
            callback.succeed();
        }

        @Override
        public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
            byte[] b = new byte[payload.remaining()];
            payload.get(b);
            binary.complete(b);
            callback.succeed();
        }
    }

    @Test
    void anImageGoesOutWholeAndUncompressed() throws Exception {
        Frames f = new Frames();
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        req.setHeader("Origin", ORIGIN);
        req.setHeader("X-Telerobot-Origin", SECRET);
        req.addExtensions("permessage-deflate"); // as every browser asks
        Session s = client.connect(f, URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req).get(5, TimeUnit.SECONDS);
        assertEquals(List.of(), s.getUpgradeResponse().getExtensions(), "no extension is agreed");
        s.sendText("{\"t\":\"hello\",\"nick\":\"bob\"}", Callback.NOOP);
        byte[] got = f.binary.get(5, TimeUnit.SECONDS);
        assertTrue(java.util.Arrays.equals(JPEG, got), "the JPEG arrives intact");
        assertEquals("2F:" + JPEG.length, f.frames.get(f.frames.size() - 1), "one final, uncompressed binary frame: " + f.frames);
        assertEquals(2, f.frames.size(), "a text header, then the image: " + f.frames);
    }
}
