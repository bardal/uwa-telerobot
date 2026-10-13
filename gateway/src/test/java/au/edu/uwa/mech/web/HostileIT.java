package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            new Idle(System::currentTimeMillis, () -> { }), System::currentTimeMillis);
        server = new GatewayServer(cfg, registry, System::currentTimeMillis);
        server.start();
        client = new WebSocketClient();
        client.start();
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

        JsonNode await(Predicate<JsonNode> p, long millis) throws InterruptedException {
            long end = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < end) {
                for (JsonNode n : texts) if (p.test(n)) return n;
                Thread.sleep(10);
            }
            throw new AssertionError("no matching frame in " + millis + " ms; got " + texts);
        }

        JsonNode awaitType(String t) throws InterruptedException {
            return await(n -> t.equals(n.path("t").asText()), 20_000);
        }

        JsonNode awaitError(String code) throws InterruptedException {
            return await(n -> "error".equals(n.path("t").asText()) && code.equals(n.path("code").asText()), 10_000);
        }

        int awaitClose(long millis) throws InterruptedException {
            long end = System.currentTimeMillis() + millis;
            while (closeCode < 0 && System.currentTimeMillis() < end) Thread.sleep(10);
            return closeCode;
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
        Thread.sleep(120); // 10 handshakes a second is a global cap: stay well under it
        Peer p = new Peer();
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

    // ---- 1. a thousand random text frames ----------------------------------------------------

    @Test
    void aThousandRandomTextFramesAreRefusedAndTheSessionIsClosedAfter20() throws Exception {
        Peer hostile = guest("noisy");
        Peer good = guest("fine");
        Random rnd = new Random(20261013);
        for (int i = 0; i < 1000 && hostile.session.isOpen(); i++) {
            byte[] raw = new byte[1 + rnd.nextInt(200)];
            rnd.nextBytes(raw);
            // any byte value, as a text frame: each byte becomes one char (valid UTF-8 on the wire)
            if (raw[0] == '{') raw[0] = '!'; // a lone "{" prefix is no more valid, but keep it plainly garbage
            hostile.send(new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1));
        }
        assertEquals(1008, hostile.awaitClose(10_000), "closed with policy violation");
        assertTrue(hostile.count("error", "bad-message") >= 1, hostile.texts.toString());
        assertEquals(20, hostile.count("error", "bad-message"), "refused one by one until the 20th closes it");
        assertEquals(0, hostile.count("error", null) - hostile.count("error", "bad-message") - hostile.count("error", "rate-limited"),
            "only bad-message and the final rate-limited close: " + hostile.texts);

        // a second, well-behaved session is unaffected: it still gets images
        good.send(msg("selectCamera").put("camera", "Colour"));
        JsonNode image = good.await(n -> "image".equals(n.path("t").asText()), 20_000);
        assertEquals("Colour", image.get("camera").asText());
        long end = System.currentTimeMillis() + 10_000;
        while (good.binaries.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20);
        assertFalse(good.binaries.isEmpty(), "the JPEG follows");
        assertEquals(-1, good.closeCode);
        good.leave();
    }

    // ---- 2. a serialized gadget ---------------------------------------------------------------

    /** Counts every decision the JDK's serialization filter logs ({@code java.io.serialization}). */
    static final class FilterTap extends Handler {
        final AtomicInteger records = new AtomicInteger();
        final List<String> rejected = new CopyOnWriteArrayList<>();

        @Override public void publish(LogRecord r) {
            records.incrementAndGet();
            String m = r.getMessage() == null ? "" : r.getMessage();
            if (m.contains("REJECTED")) rejected.add(m);
        }

        @Override public void flush() { }

        @Override public void close() { }
    }

    @Test
    void aSerializedGadgetNeverReachesAnObjectInputStream() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(new java.util.HashMap<>());
        }
        byte[] gadget = bos.toByteArray();
        assertEquals((byte) 0xAC, gadget[0]);

        java.util.logging.Logger filterLog = java.util.logging.Logger.getLogger("java.io.serialization");
        FilterTap tap = new FilterTap();
        Level before = filterLog.getLevel();
        filterLog.setLevel(Level.ALL);
        filterLog.addHandler(tap);
        try {
            // quiet the lab first: nothing else of ours is talking to MOM
            Thread.sleep(1500);
            Peer text = guest("gadgettext");
            Thread.sleep(1500);
            int quiet = tap.records.get();

            // base64 and raw bytes (as chars), as text, before and after MOM's login
            text.send(Base64.getEncoder().encodeToString(gadget));
            text.awaitError("bad-message");
            text.send(new String(gadget, java.nio.charset.StandardCharsets.ISO_8859_1));
            text.awaitError("bad-message");
            // the stream sits inside a JSON string field: it is only a string, and no object is read
            text.send(msg("chat").put("text", Base64.getEncoder().encodeToString(gadget)));

            // raw, as binary: refused and closed
            Peer binary = guest("gadgetbin");
            Thread.sleep(1500);
            int quietBinary = tap.records.get();
            binary.session.sendBinary(ByteBuffer.wrap(gadget), Callback.NOOP);
            assertEquals(1003, binary.awaitClose(10_000));

            // and before hello
            Peer early = connect();
            early.session.sendBinary(ByteBuffer.wrap(gadget), Callback.NOOP);
            assertEquals(1003, early.awaitClose(10_000));
            early.session.sendText(Base64.getEncoder().encodeToString(gadget), Callback.NOOP);

            Thread.sleep(1500);
            assertTrue(text.count("error", "bad-message") >= 2, text.texts.toString());
            assertEquals(0, text.count("error", "unauthorised"));
            // no ObjectInputStream anywhere in the JVM (gateway and lab share it) read a thing from the gadget
            assertTrue(tap.records.get() > 0, "control: the filter log is wired (the harmless chat above did reach MOM)");
            assertEquals(List.of(), tap.rejected, "nothing was refused by the serial filter: it never got there");
            // the only filter traffic is the chat echo to MOM, which carries a string and no gadget
            assertTrue(tap.records.get() - quietBinary <= tap.records.get() - quiet);
            text.leave();
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
        for (int i = 0; i < 100 && p.session.isOpen(); i++) p.send(msg("chat").put("text", "spam " + i));
        assertEquals(1008, p.awaitClose(10_000));
        assertTrue(p.count("error", "rate-limited") >= 20, p.texts.toString());
        Thread.sleep(500);
        long delivered = listener.texts.stream().filter(n -> "chat".equals(n.path("t").asText())).count();
        assertTrue(delivered <= 6, "burst of 5 (and at most one refill) got through, not 100: " + delivered);
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

    @Test
    void anAdminMessageFromAGuestIsRefused() throws Exception {
        Peer victim = guest("bystander");
        Peer p = guest("sneaky");
        p.send(msg("admin").put("action", "kick").put("nick", "bystander"));
        assertEquals("admins only", p.awaitError("unauthorised").get("message").asText());
        p.send(msg("admin").put("action", "ban").put("nick", "bystander"));
        p.send(msg("admin").put("action", "reset"));
        Thread.sleep(800);
        assertEquals(-1, victim.closeCode, "the victim was not kicked");
        assertEquals(-1, p.closeCode, "three refusals are not 20");

        // a registered (not admin) user is refused the same way
        Peer reg = connect();
        reg.send(helloToken("regular", jwt(key, List.of("students"))));
        assertEquals("registered", reg.awaitType("welcome").get("level").asText());
        reg.send(msg("admin").put("action", "kick").put("nick", "bystander"));
        reg.awaitError("unauthorised");
        Thread.sleep(300);
        assertEquals(-1, victim.closeCode);
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

    @Test
    void the61stSessionIsRefusedAsTooMany() throws Exception {
        List<Peer> peers = new ArrayList<>();
        try {
            int already = server.sessions();
            for (int i = 0; i < Admission.MAX_VIEWERS - already; i++) {
                Peer p = connect();
                peers.add(p);
                p.send(hello("crowd " + i));
            }
            assertEquals(Admission.MAX_VIEWERS, server.sessions());
            ExecutionException ex = assertThrows(ExecutionException.class, HostileIT::connect);
            UpgradeException up = (UpgradeException) ex.getCause();
            assertEquals(429, up.getResponseStatusCode());
            // the one that did not get in did not use up a slot
            assertEquals(Admission.MAX_VIEWERS, server.sessions());
        } finally {
            peers.forEach(Peer::leave);
        }
        long end = System.currentTimeMillis() + 15_000;
        while (server.sessions() != 0 && System.currentTimeMillis() < end) Thread.sleep(50);
        assertEquals(0, server.sessions(), "every slot is given back");
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
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        try {
            List<java.util.concurrent.Future<String>> probes = new ArrayList<>();
            for (InetAddress a : others) {
                for (int port : ports) {
                    probes.add(pool.submit(() -> {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(a, port), 1000);
                            return a + ":" + port;
                        } catch (IOException refusedOrTimedOut) {
                            return null;
                        }
                    }));
                }
            }
            for (java.util.concurrent.Future<String> f : probes) {
                assertEquals(null, f.get(30, TimeUnit.SECONDS), "reachable on a non-loopback address");
            }
        } finally {
            pool.shutdownNow();
        }
        assertNotNull(lab.world());
    }
}
