package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.exceptions.UpgradeException;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Hostile clients against the whole gateway in production mode (origin secret, Turnstile faked, a
 * local RSA issuer in place of Cognito) in front of the real lab ({@link LabFixture}).
 */
@Tag("hostile")
class HostileIT {
    static final String ORIGIN = "https://robot.example";
    static final String SECRET = "s3cret-origin";
    static final String ISS = "https://cognito-idp.example/pool";
    static final String CLIENT = "webclient";
    static final AtomicInteger IPS = new AtomicInteger();

    /** The gateway's clock and timers: rate limits, the handshake cap and leases move only with it. */
    static final ManualTime TIME = new ManualTime();
    static LabFixture lab;
    static RSAKey key;
    static RSAKey otherKey;
    static SessionRegistry registry;
    static GatewayServer server;
    static WebSocketClient client;

    @BeforeAll
    static void start() throws Exception {
        Main.installSerialFilter(); // as production: the gateway's JVM runs under the allowlist
        lab = LabFixture.shared();
        key = new RSAKeyGenerator(2048).keyID("k1").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        GatewayConfig cfg = new GatewayConfig(0, "127.0.0.1", lab.momPort(), lab.adminPort(), List.of(ORIGIN),
            SECRET, false, null, null, ISS, List.of(CLIENT), "ts", lab.gatewayPins(), null, false, "hostile", null,
            null, null);
        Auth auth = new Auth(cfg, (token, ip) -> true,
            Auth.processor(cfg, new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK()))));
        registry = new SessionRegistry(cfg, auth, new Bans(new au.edu.uwa.mech.web.cloud.MemoryParamStore()),
            new Idle(TIME, () -> { }), TIME);
        server = new GatewayServer(cfg, registry, TIME, GatewayServer.Timing.DEFAULT, TIME.scheduler());
        server.start();
        client = new WebSocketClient();
        client.start();
    }

    /** Every peer a test opened. They are all closed after it, pass or fail: one left holding control would
     * keep the next test's guest from taking it for 180 s, and a left session from an empty gateway. */
    static final List<Peer> OPEN = new CopyOnWriteArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void leaveAll() {
        OPEN.forEach(Peer::leave);
        OPEN.clear();
    }

    @AfterAll
    static void stop() throws Exception {
        if (client != null) client.stop();
        if (registry != null) registry.shutdown();
        if (server != null) server.stop();
    }

    // ---- a recording client ------------------------------------------------------------------

    public static final class Peer implements Session.Listener.AutoDemanding {
        final List<JsonNode> texts = new CopyOnWriteArrayList<>();
        final List<byte[]> binaries = new CopyOnWriteArrayList<>();
        volatile Session session;
        volatile int closeCode = -1;

        @Override public void onWebSocketOpen(Session s) { session = s; }

        @Override public void onWebSocketText(String m) {
            try {
                texts.add(Protocol.mapper().readTree(m));
            } catch (IOException e) {
                throw new IllegalStateException(m, e);
            }
        }

        @Override public void onWebSocketBinary(ByteBuffer p, Callback cb) {
            byte[] b = new byte[p.remaining()];
            p.get(b);
            binaries.add(b);
            cb.succeed();
        }

        @Override public void onWebSocketClose(int code, String reason) { closeCode = code; }

        void send(String json) { session.sendText(json, Callback.NOOP); }

        void send(ObjectNode json) { send(json.toString()); }

        JsonNode await(Predicate<JsonNode> p, long millis) {
            JsonNode[] found = {null};
            Await.until(() -> "no matching frame; got " + texts, () -> {
                for (JsonNode n : texts) if (p.test(n)) return (found[0] = n) != null;
                return false;
            }, millis);
            return found[0];
        }

        JsonNode awaitType(String t) {
            return await(n -> t.equals(n.path("t").asText()), 20_000);
        }

        JsonNode awaitError(String code) {
            return await(n -> "error".equals(n.path("t").asText()) && code.equals(n.path("code").asText()), 10_000);
        }

        int awaitClose(long millis) {
            Await.until("closed", () -> closeCode >= 0, millis);
            return closeCode;
        }

        /** The latest users list this peer was sent. */
        List<String> users() {
            List<String> nicks = List.of();
            for (JsonNode n : texts) {
                if (!"users".equals(n.path("t").asText())) continue;
                List<String> l = new ArrayList<>();
                n.path("nicks").forEach(x -> l.add(x.asText()));
                nicks = l;
            }
            return nicks;
        }

        long count(String t, String code) {
            return texts.stream().filter(n -> t.equals(n.path("t").asText())
                && (code == null || code.equals(n.path("code").asText()))).count();
        }

        void leave() {
            try {
                if (session != null && session.isOpen()) session.close();
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
    }

    static String nextViewer() {
        int n = IPS.incrementAndGet();
        return "10." + (n / 250 % 250) + "." + (n % 250) + ".7:4000";
    }

    /** A fully valid handshake: the right Origin and secret, from a fresh viewer address. */
    static Peer connect() throws Exception {
        return connect(ORIGIN, SECRET);
    }

    static Peer connect(String origin, String secret) throws Exception {
        TIME.advance(120); // 10 handshakes a second is a global cap: stay well under it
        Peer p = new Peer();
        OPEN.add(p);
        ClientUpgradeRequest req = new ClientUpgradeRequest();
        if (origin != null) req.setHeader("Origin", origin);
        if (secret != null) req.setHeader(Admission.SECRET_HEADER, secret);
        req.setHeader(Admission.VIEWER_ADDRESS_HEADER, nextViewer());
        client.connect(p, URI.create("ws://127.0.0.1:" + server.port() + "/ws"), req).get(10, TimeUnit.SECONDS);
        return p;
    }

    static String hello(String nick) {
        ObjectNode o = Protocol.mapper().createObjectNode();
        o.put("t", "hello");
        o.put("nick", nick);
        o.put("turnstile", "any-token");
        return o.toString();
    }

    static String helloToken(String nick, String idToken) {
        ObjectNode o = Protocol.mapper().createObjectNode();
        o.put("t", "hello");
        o.put("nick", nick);
        o.put("idToken", idToken);
        return o.toString();
    }

    static Peer guest(String nick) throws Exception {
        Peer p = connect();
        p.send(hello(nick));
        p.awaitType("welcome");
        return p;
    }

    static String jwt(RSAKey signer, List<String> groups) throws Exception {
        Date now = new Date();
        JWTClaimsSet.Builder c = new JWTClaimsSet.Builder().issuer(ISS).audience(CLIENT).subject("user-1")
            .claim("token_use", "id").issueTime(now).expirationTime(new Date(now.getTime() + 3_600_000));
        if (!groups.isEmpty()) c.claim("cognito:groups", groups);
        SignedJWT j = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), c.build());
        j.sign(new RSASSASigner(signer));
        return j.serialize();
    }

    static ObjectNode msg(String t) {
        ObjectNode o = Protocol.mapper().createObjectNode();
        o.put("t", t);
        return o;
    }

    /**
     * The server closed a flooding client. The close frame it sends can be lost to a TCP reset when the client
     * is still writing (the client then reports 1006 instead of 1008), so accept either code and confirm on
     * the server side that the session is really gone.
     */
    static void assertClosedByServer(Peer p, String nick) throws Exception {
        int code = p.awaitClose(10_000);
        assertTrue(code == 1008 || code == 1006, "closed by the server (1008, or 1006 when the reset beat the close frame): " + code);
        Await.until("the gateway no longer has a session for " + nick, () -> registry.byNick(nick).isEmpty());
    }

    // ---- 1. a thousand random text frames ----------------------------------------------------

    @Test
    void aThousandRandomTextFramesAreRefusedAndTheSessionIsClosedAfter20() throws Exception {
        Peer hostile = guest("noisy");
        Peer good = controller("fine");
        // the well-behaved session watches a non-default camera while its robot moves
        good.send(msg("selectCamera").put("camera", "ZCam"));
        good.await(n -> "image".equals(n.path("t").asText()) && "ZCam".equals(n.path("camera").asText()), 20_000);
        good.send(msg("command").put("script", "x200,y250,z100;x330,y250,z100;x200,y250,z100"));
        good.await(n -> "commandStatus".equals(n.path("t").asText()) && "queued".equals(n.path("state").asText()), 10_000);
        int before = good.binaries.size();

        Random rnd = new Random(20261013);
        for (int i = 0; i < 1000 && hostile.session.isOpen(); i++) {
            byte[] raw = new byte[1 + rnd.nextInt(200)];
            rnd.nextBytes(raw);
            // any byte value, as a text frame: each byte becomes one char (valid UTF-8 on the wire)
            if (raw[0] == '{') raw[0] = '!'; // a lone "{" prefix is no more valid, but keep it plainly garbage
            hostile.send(new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1));
            // the good session asks mid-flood too. Twice: with the one after the flood, that is within its
            // camera allowance (a burst of 3, selectCamera used one, plus a second's refill before each ask)
            // however late the gateway gets to them; the allowance is reckoned when it does.
            if (i == 0 || i == 10) {
                TIME.advance(1_100);
                good.send(msg("newImage").put("camera", "ZCam"));
            }
        }
        assertClosedByServer(hostile, "noisy");
        assertEquals(20, hostile.count("error", "bad-message"), "refused one by one until the 20th closes it");
        assertEquals(0, hostile.count("error", null) - hostile.count("error", "bad-message") - hostile.count("error", "rate-limited"),
            "only bad-message and the final rate-limited close: " + hostile.texts);

        // the good session got camera frames during and after the flood, and was never closed or refused
        Await.until("ZCam frames kept arriving during the flood", () -> good.binaries.size() > before, 20_000);
        TIME.advance(1_100);
        int mark = good.binaries.size();
        good.send(msg("newImage").put("camera", "ZCam"));
        Await.until("a request after the flood is still answered", () -> good.binaries.size() > mark, 20_000);
        assertTrue(good.binaries.stream().allMatch(b -> b.length > 2 && b[0] == (byte) 0xFF && b[1] == (byte) 0xD8),
            "every binary frame is a JPEG");
        assertEquals(0, good.count("error", null), "the good session was never refused: " + good.texts);
        assertEquals(-1, good.closeCode);
    }

    // ---- 1b. a camera flood ------------------------------------------------------------------

    /**
     * The camera server's shutter is idle: the arm is at rest and its ShutterThread (which photographs every
     * 500 ms while the arm moves, then once more) is back waiting for the next move.
     */
    static boolean shutterIdle() {
        if (lab.world().snapshot().moving) return false;
        for (var e : Thread.getAllStackTraces().entrySet()) {
            StackTraceElement[] st = e.getValue();
            for (int i = 0; i < st.length; i++) {
                if (!st[i].getClassName().endsWith("Server$ShutterThread")) continue;
                // waiting on moveSignal: Object.wait called straight from ShutterThread.run
                return e.getKey().getState() == Thread.State.WAITING && i > 0
                    && st[i - 1].getClassName().equals("java.lang.Object") && st[i - 1].getMethodName().startsWith("wait");
            }
        }
        return true; // no shutter at all
    }

    @Test
    void twentyNewImagesAtOnceReachTheCameraServerOnlyThreeTimes() throws Exception {
        // only photos asked for are counted: the shutter is idle, and stays so (nothing moves the arm)
        Await.until("the camera server's shutter is idle", HostileIT::shutterIdle, 20_000);
        AtomicInteger photos = new AtomicInteger();
        au.edu.uwa.mech.devices.camera.Camera cam = lab.camera("YCam");
        au.edu.uwa.mech.devices.camera.ImageListener count = (source, image) -> photos.incrementAndGet();
        Peer p = guest("shutterbug");
        try {
            cam.addImageListener(count);
            // all at one instant on the gateway's clock: its burst of 3, and no refill
            for (int i = 0; i < 20; i++) p.send(msg("newImage").put("camera", "YCam"));
            // every request gets an answer: 17 refusals (fewer than the 20 that close a session), so exactly
            // 3 went to the camera server, and each takes one photo
            Await.until(() -> "refusals " + p.count("error", "rate-limited"),
                () -> p.count("error", "rate-limited") == 17, 10_000);
            Await.until(() -> "photos " + photos.get(), () -> photos.get() == 3, 10_000);
            assertEquals(17, p.count("error", null), p.texts.toString());
            assertEquals(-1, p.closeCode);
        } finally {
            cam.removeImageListener(count);
            p.leave();
        }
    }

    // ---- 2. a serialized gadget ---------------------------------------------------------------

    /** Counts every decision the JDK's serialization filter logs ({@code java.io.serialization}). */
    static final class FilterTap extends Handler {
        final List<String> all = new CopyOnWriteArrayList<>();
        final List<String> rejected = new CopyOnWriteArrayList<>();

        @Override public void publish(LogRecord r) {
            String m = (r.getMessage() == null ? "" : r.getMessage()) + " "
                + (r.getParameters() == null ? "" : java.util.Arrays.toString(r.getParameters()));
            all.add(m);
            if (m.contains("REJECTED")) rejected.add(m);
        }

        @Override public void flush() { }

        @Override public void close() { }
    }

    @Test
    void aSerializedGadgetNeverReachesAnObjectInputStream() throws Exception {
        // java.io.File is NOT on the allowlist (java.util.HashMap would be allowed, which proves nothing)
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(new java.io.File("/etc/passwd"));
        }
        byte[] gadget = bos.toByteArray();
        assertEquals((byte) 0xAC, gadget[0]);
        assertEquals((byte) 0xED, gadget[1]);

        java.util.logging.Logger filterLog = java.util.logging.Logger.getLogger("java.io.serialization");
        FilterTap tap = new FilterTap();
        Level before = filterLog.getLevel();
        filterLog.setLevel(Level.ALL);
        filterLog.addHandler(tap);
        try {
            // control 1: under the installed filter this gadget IS rejected, and the tap sees that
            assertThrows(java.io.InvalidClassException.class,
                () -> new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(gadget)).readObject());
            assertTrue(tap.rejected.stream().anyMatch(m -> m.contains("java.io.File")),
                "the tap records a rejected java.io.File: " + tap.rejected);
            // control 2: MOM traffic of a harmless message is visible too (the lab also chatters on its own:
            // robot and camera polling, so the total count cannot be expected to stand still)
            Peer control = guest("control");
            int beforeChat = tap.all.size();
            control.send(msg("chat").put("text", "harmless"));
            control.await(n -> "chat".equals(n.path("t").asText()), 10_000);
            assertTrue(tap.all.size() > beforeChat, "the tap sees MOM deserialising a chat");

            Peer text = guest("gadgettext");
            Peer binary = guest("gadgetbin");
            Peer early = connect(); // no hello yet
            // (so that its leaving, below, is something the control session sees happen)
            Await.until(() -> "users " + control.users(), () -> control.users().contains("gadgetbin"), 10_000);
            int mark = tap.all.size();
            int rejectedMark = tap.rejected.size();

            // every shape of the gadget
            text.send(Base64.getEncoder().encodeToString(gadget));
            text.send(new String(gadget, java.nio.charset.StandardCharsets.ISO_8859_1));
            binary.session.sendBinary(ByteBuffer.wrap(gadget), Callback.NOOP);
            early.session.sendBinary(ByteBuffer.wrap(gadget), Callback.NOOP);

            assertEquals(1003, binary.awaitClose(10_000), "binary: closed as unsupported data");
            assertEquals(1003, early.awaitClose(10_000), "binary before hello: closed as unsupported data");
            Await.until(() -> text.texts.toString(), () -> text.count("error", "bad-message") == 2, 10_000);
            assertEquals(-1, text.closeCode, "two refusals do not close a text session");
            // Sync points instead of a pause. MOM reads each connection's packets in order, so anything the
            // gateway had (wrongly) forwarded is deserialised before what follows it on the same connection:
            // - gadgettext: a chat sent after the gadgets reaches the control session;
            // - gadgetbin: its logout, sent when the gateway closed it, takes it off the control's user list.
            // (early never said hello, so it never had a MOM connection to forward anything on.)
            text.send(msg("chat").put("text", "after the gadgets"));
            control.await(n -> "chat".equals(n.path("t").asText()) && "after the gadgets".equals(n.path("text").asText()),
                10_000);
            Await.until(() -> "users " + control.users(), () -> !control.users().contains("gadgetbin"), 10_000);

            List<String> window = new ArrayList<>(tap.all.subList(mark, tap.all.size()));
            assertEquals(rejectedMark, tap.rejected.size(), "nothing was rejected by the serial filter");
            assertTrue(window.stream().noneMatch(m -> m.contains("java.io.File") || m.contains("REJECTED")),
                "no packet carrying the gadget was ever deserialised (the window holds only the lab's own "
                    + window.size() + " routine records): " + window.stream().distinct().limit(8).toList());
            text.leave();
            control.leave();
        } finally {
            filterLog.removeHandler(tap);
            filterLog.setLevel(before);
        }
    }

    // ---- 3 and 4. scripts ----------------------------------------------------------------------

    private Peer controller(String nick) throws Exception {
        Peer p = guest(nick);
        p.send(msg("takeControl"));
        p.await(n -> "controller".equals(n.path("t").asText()) && nick.equals(n.path("nick").asText()), 20_000);
        return p;
    }

    @Test
    void aScriptOf5000CharactersIsRefused() throws Exception {
        Peer p = controller("longscript");
        p.send(msg("command").put("script", "x100;".repeat(1000)));
        // over the 4 KB frame limit: the socket is closed before the gateway parses it
        assertEquals(1009, p.awaitClose(10_000));
        assertEquals(0, p.count("commandStatus", null), "nothing was queued");

        // within the frame limit but over 400 characters: refused by the script check
        Peer q = controller("longscript2");
        q.send(msg("command").put("script", "x100;".repeat(81)));
        JsonNode err = q.awaitError("bad-message");
        assertTrue(err.get("message").asText().contains("400"), err.toString());
        assertEquals(0, q.count("commandStatus", null));
        q.leave();
    }

    @Test
    void aScriptWith31WaypointsIsRefused() throws Exception {
        Peer p = controller("manypoints");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 31; i++) sb.append(i == 0 ? "" : ";").append("x").append(200 + i);
        p.send(msg("command").put("script", sb.toString()));
        JsonNode err = p.awaitError("bad-message");
        assertTrue(err.get("message").asText().contains("30 waypoints"), err.toString());
        assertEquals(0, p.count("commandStatus", null), "nothing was queued");
        p.leave();
    }

    // ---- 5. chat flood -------------------------------------------------------------------------

    @Test
    void a100ChatMessagesInASecondAreRateLimitedAndTheSessionIsClosed() throws Exception {
        Peer p = guest("chatty");
        Peer listener = guest("listener");
        Await.until(() -> "users " + listener.users(), () -> listener.users().contains("chatty"), 10_000);
        // positive control: one ordinary chat from the same peer is delivered
        p.send(msg("chat").put("text", "hello before the flood"));
        listener.await(n -> "chat".equals(n.path("t").asText())
            && "hello before the flood".equals(n.path("text").asText()), 10_000);

        // all at one instant on the gateway's clock: what is left of the burst of 5, and no refill
        for (int i = 0; i < 100 && p.session.isOpen(); i++) p.send(msg("chat").put("text", "spam " + i));
        assertClosedByServer(p, "chatty");
        p.awaitError("rate-limited");
        assertTrue(p.count("error", "rate-limited") >= 20, p.texts.toString());
        // chatty's logout follows its chats on its MOM connection, so once the listener sees it leave,
        // every chat of its that MOM will ever pass on has been passed on
        Await.until(() -> "users " + listener.users(), () -> !listener.users().contains("chatty"), 10_000);
        assertEquals(5, listener.count("chat", null),
            "the control and the 4 left of the burst of 5, not 100: " + listener.texts);
        listener.leave();
    }

    // ---- 6. forged tokens ----------------------------------------------------------------------

    @Test
    void forgedTokensAreRefused() throws Exception {
        Date now = new Date();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).audience(CLIENT).subject("evil")
            .claim("token_use", "id").claim("cognito:groups", List.of("admin")).issueTime(now)
            .expirationTime(new Date(now.getTime() + 3_600_000)).build();
        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("k1").build(), claims);
        hmac.sign(new MACSigner(new byte[32]));
        String[] forged = {
            new PlainJWT(claims).serialize(),                 // alg none
            jwt(otherKey, List.of("admin")),                  // another key
            hmac.serialize(),                                 // HS256
            "eyJhbGciOiJub25lIn0.e30.",                       // a bare none header
            "not.a.jwt"};
        for (String token : forged) {
            Peer p = connect();
            p.send(helloToken("forger", token));
            p.awaitError("unauthorised");
            assertEquals(1008, p.awaitClose(10_000));
            assertEquals(0, p.count("welcome", null), "no welcome for " + token);
        }
        // a genuine token from the trusted key does get in (the checks above are not vacuous)
        Peer ok = connect();
        ok.send(helloToken("genuine", jwt(key, List.of())));
        assertEquals("registered", ok.awaitType("welcome").get("level").asText());
        ok.leave();
    }

    // ---- 7. admin from a guest -----------------------------------------------------------------

    /** The robot pose and every block position, as one comparable string. */
    static String worldKey() {
        var snap = lab.world().snapshot();
        StringBuilder sb = new StringBuilder("pose " + snap.pose.x + "," + snap.pose.y + "," + snap.pose.z);
        for (var b : snap.blocks) sb.append(' ').append(b.name).append(' ').append(b.getX()).append(',')
            .append(b.getY()).append(',').append(b.getZ());
        return sb.toString();
    }

    @Test
    void anAdminMessageFromAGuestIsRefused() throws Exception {
        // the victim moves the robot away from its start position, so a world "reset" would be visible
        Peer victim = controller("bystander");
        victim.send(msg("command").put("script", "x300,y250,z120"));
        victim.await(n -> "commandStatus".equals(n.path("t").asText()) && "done".equals(n.path("state").asText()), 60_000);
        Await.until("the arm is at rest", () -> !lab.world().snapshot().moving, 15_000);
        String world = worldKey();
        assertTrue(world.startsWith("pose 300.0,250.0,120.0"), "the robot is away from its start: " + world);

        Peer p = guest("sneaky");
        p.send(msg("admin").put("action", "kick").put("nick", "bystander"));
        assertEquals("admins only", p.awaitError("unauthorised").get("message").asText());
        p.send(msg("admin").put("action", "ban").put("nick", "bystander"));
        p.send(msg("admin").put("action", "reset"));
        Await.until(() -> "each of the three attempts was refused: " + p.texts, () -> p.count("error", "unauthorised") == 3,
            10_000);
        assertEquals(-1, victim.closeCode, "the victim was not kicked");
        assertEquals(-1, p.closeCode, "three refusals are not 20");
        assertEquals(world, worldKey(), "the lab was not reset");
        // the ban did not take: the victim can still do things
        victim.send(msg("activity"));
        victim.send(msg("chat").put("text", "still here"));
        victim.await(n -> "chat".equals(n.path("t").asText()) && "still here".equals(n.path("text").asText()), 10_000);

        // a registered (not admin) user is refused the same way
        Peer reg = connect();
        reg.send(helloToken("regular", jwt(key, List.of("students"))));
        assertEquals("registered", reg.awaitType("welcome").get("level").asText());
        reg.send(msg("admin").put("action", "kick").put("nick", "bystander"));
        reg.send(msg("admin").put("action", "reset"));
        Await.until(() -> reg.texts.toString(), () -> reg.count("error", "unauthorised") == 2, 10_000);
        assertEquals(-1, victim.closeCode);
        assertEquals(world, worldKey());
        reg.leave();
        p.leave();
        victim.leave();
    }

    // ---- 8 and 9. the handshake ----------------------------------------------------------------

    private static void assertRefusedAtUpgrade(int status, String origin, String secret) {
        ExecutionException ex = assertThrows(ExecutionException.class, () -> connect(origin, secret));
        assertTrue(ex.getCause() instanceof UpgradeException, String.valueOf(ex.getCause()));
        assertEquals(status, ((UpgradeException) ex.getCause()).getResponseStatusCode());
    }

    @Test
    void anEvilOriginIsRefusedAtTheUpgrade() {
        assertRefusedAtUpgrade(403, "https://evil.example", SECRET);
        assertRefusedAtUpgrade(403, null, SECRET);
        assertRefusedAtUpgrade(403, ORIGIN + ".evil.example", SECRET);
    }

    @Test
    void aMissingOrWrongSecretHeaderIsRefusedAtTheUpgrade() {
        assertRefusedAtUpgrade(403, ORIGIN, null);
        assertRefusedAtUpgrade(403, ORIGIN, "");
        assertRefusedAtUpgrade(403, ORIGIN, SECRET + "x");
        assertRefusedAtUpgrade(403, ORIGIN, SECRET.toUpperCase());
    }

    // ---- 10. 61 sessions -----------------------------------------------------------------------

    /** A hand-written upgrade request; returns the raw HTTP response (status line, headers, body). */
    static String rawUpgrade() throws Exception {
        try (Socket s = new Socket("127.0.0.1", server.port())) {
            s.setSoTimeout(3000);
            String req = "GET /ws HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nOrigin: " + ORIGIN + "\r\n"
                + Admission.SECRET_HEADER + ": " + SECRET + "\r\n" + Admission.VIEWER_ADDRESS_HEADER + ": "
                + nextViewer() + "\r\n\r\n";
            s.getOutputStream().write(req.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            java.io.ByteArrayOutputStream got = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            try {
                int n;
                while ((n = s.getInputStream().read(buf)) > 0) got.write(buf, 0, n);
            } catch (java.net.SocketTimeoutException done) {
                // the server may keep the connection open after its answer
            }
            return got.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void the61stSessionIsRefusedAsTooMany() throws Exception {
        Await.until(() -> "start from an empty gateway: " + server.sessions(), () -> server.sessions() == 0, 15_000);
        List<Peer> peers = new ArrayList<>();
        try {
            for (int i = 0; i < Admission.MAX_VIEWERS; i++) {
                Peer p = connect();
                peers.add(p);
                p.send(hello("crowd " + i));
            }
            assertEquals(Admission.MAX_VIEWERS, server.sessions());
            TIME.advance(1200); // the handshake-rate window is clear
            String response = rawUpgrade();
            assertTrue(response.startsWith("HTTP/1.1 429"), response);
            assertTrue(response.contains("<title>Error 429 too many</title>") && response.contains("<td>too many</td>"),
                "the page says only the generic too many: " + response);
            assertFalse(response.contains("viewer"), "the reason is not spelled out: " + response);
            ExecutionException ex = assertThrows(ExecutionException.class, HostileIT::connect);
            assertEquals(429, ((UpgradeException) ex.getCause()).getResponseStatusCode());
            // the ones that did not get in did not use up a slot
            assertEquals(Admission.MAX_VIEWERS, server.sessions());
        } finally {
            peers.forEach(Peer::leave);
        }
        Await.until(() -> "every slot is given back: " + server.sessions(), () -> server.sessions() == 0, 15_000);
    }

    // ---- 11. nicks -----------------------------------------------------------------------------

    @Test
    void scriptAndSpoofNicksAreRefused() throws Exception {
        for (String nick : List.of("<img src=x onerror=alert(1)>", "‮admin", "ad‮min", "admin​",
                "a".repeat(21), "", "  ", "bob\nalice")) {
            Peer p = connect();
            p.send(hello(nick));
            p.awaitError("bad-message");
            assertEquals(1008, p.awaitClose(10_000), "nick " + nick.replace("‮", "<RLO>"));
            assertEquals(0, p.count("welcome", null));
            assertEquals(0, p.count("users", null), "never listed");
        }
    }

    @Test
    void reservedNicksAreForAdminsOnly() throws Exception {
        for (String nick : List.of("Admin", "ro.bot", "Sys_Tem", "barney")) {
            Peer g = connect();
            g.send(hello(nick));
            assertEquals("That nickname is reserved", g.awaitError("bad-message").get("message").asText(), nick);
            assertEquals(1008, g.awaitClose(10_000));
            assertEquals(0, g.count("welcome", null));
        }
        Peer reg = connect();
        reg.send(helloToken("Moderator", jwt(key, List.of("students"))));
        assertEquals("That nickname is reserved", reg.awaitError("bad-message").get("message").asText());
        assertEquals(0, reg.count("welcome", null));

        Peer admin = connect();
        admin.send(helloToken("Moderator", jwt(key, List.of("admin"))));
        JsonNode w = admin.awaitType("welcome");
        assertEquals("admin", w.get("level").asText());
        assertEquals("Moderator", w.get("nick").asText(), "an admin may use any valid nick");
        admin.leave();
    }

    // ---- 12. the port probe --------------------------------------------------------------------

    @Test
    void theLabListensOnLoopbackOnly() throws Exception {
        int[] ports = {lab.momPort(), lab.momPort() + 1, lab.adminPort(), lab.ports().control(),
            lab.ports().position()};
        // each lab port answers on loopback ...
        for (int port : ports) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
                assertTrue(s.getInetAddress().isLoopbackAddress());
            }
        }
        // ... and on no other local address
        List<InetAddress> others = new ArrayList<>();
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp() || nif.isLoopback()) continue;
            for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                if (!a.isLoopbackAddress() && !a.isAnyLocalAddress() && !a.isLinkLocalAddress()) others.add(a);
            }
        }
        assumeFalse(others.isEmpty(), "this host has no non-loopback address");

        // Two controls decide which addresses can say anything. On a usable address (a) a socket bound to
        // 0.0.0.0 IS reachable, and (b) a port nobody listens on is actively REFUSED (a firewall that drops
        // silently would make every probe time out, which proves nothing).
        int closedPort;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        List<InetAddress> reachable = new ArrayList<>();
        try (java.net.ServerSocket anyBound = new java.net.ServerSocket(0, 50, InetAddress.getByName("0.0.0.0"))) {
            for (InetAddress a : others) {
                boolean listensReachable;
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(a, anyBound.getLocalPort()), 1000);
                    listensReachable = true;
                } catch (IOException cannotReachEvenThat) {
                    listensReachable = false;
                }
                boolean refusesClosed;
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(a, closedPort), 1000);
                    refusesClosed = false;
                } catch (java.net.ConnectException refused) {
                    refusesClosed = true;
                } catch (IOException timedOut) {
                    refusesClosed = false;
                }
                if (listensReachable && refusesClosed) reachable.add(a);
            }
        }
        assumeFalse(reachable.isEmpty(), "no non-loopback address of this host both accepts a socket bound to "
            + "0.0.0.0 and visibly refuses a closed port, so a refusal on the lab ports would prove nothing");

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        try {
            List<java.util.concurrent.Future<String>> probes = new ArrayList<>();
            for (InetAddress a : reachable) {
                for (int port : ports) {
                    probes.add(pool.submit(() -> {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(a, port), 2000);
                            return a + ":" + port + " is reachable";
                        } catch (java.net.ConnectException refused) {
                            return null; // actively refused: nothing listens on that address
                        } catch (IOException other) {
                            return a + ":" + port + " gave " + other + " (not a refusal)";
                        }
                    }));
                }
            }
            for (java.util.concurrent.Future<String> f : probes) {
                assertEquals(null, f.get(30, TimeUnit.SECONDS), "lab port on a non-loopback address");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Local addresses the OS reports listening sockets on, by port (from {@code netstat -an}). */
    static java.util.Map<Integer, java.util.Set<String>> listeners() throws Exception {
        Process p = new ProcessBuilder("netstat", "-an").redirectErrorStream(true).start();
        java.util.Map<Integer, java.util.Set<String>> out = new java.util.HashMap<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.contains("LISTEN")) continue;
                for (String token : line.trim().split(" +")) {
                    int colon = token.lastIndexOf(':');
                    if (colon > 0 && token.substring(colon + 1).matches("[0-9]+")) {
                        out.computeIfAbsent(Integer.parseInt(token.substring(colon + 1)), k -> new java.util.HashSet<>())
                            .add(token.substring(0, colon));
                        break; // the first address:port is the local one
                    }
                }
            }
        }
        p.waitFor(20, TimeUnit.SECONDS);
        return out;
    }

    @Test
    void theOperatingSystemShowsTheLabPortsListeningOnLoopbackOnly() throws Exception {
        java.util.Map<Integer, java.util.Set<String>> listening;
        try {
            listening = listeners();
        } catch (IOException noNetstat) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "netstat is not available: " + noNetstat);
            return;
        }
        // control: the gateway itself listens on every interface, and the parser sees that
        java.util.Set<String> gateway = listening.get(server.port());
        assertTrue(gateway != null && gateway.stream().anyMatch(a -> a.equals("0.0.0.0") || a.equals("[::]")
            || a.equals("::") || a.equals("*")), "control: the gateway's wildcard bind is visible: " + gateway);
        // the lab: MOM, its bulk port, the lab admin port, the robot's control and position ports
        int[] ports = {lab.momPort(), lab.momPort() + 1, lab.adminPort(), lab.ports().control(),
            lab.ports().position()};
        for (int port : ports) {
            java.util.Set<String> addresses = listening.get(port);
            assertTrue(addresses != null && !addresses.isEmpty(), "port " + port + " is listening: " + listening.keySet());
            for (String a : addresses) {
                assertTrue(a.equals("127.0.0.1") || a.equals("[::1]") || a.equals("::1"),
                    "port " + port + " listens on " + a + ", not loopback only");
            }
        }
    }
}
