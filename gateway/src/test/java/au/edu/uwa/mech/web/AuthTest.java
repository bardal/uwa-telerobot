package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.*;

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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthTest {
    static final String ISS = "https://cognito-idp.ap-southeast-2.amazonaws.com/pool";
    static final String WEB = "webclient";
    static final String SMOKE = "smokeclient";
    static RSAKey key;
    static RSAKey otherKey;

    @BeforeAll
    static void keys() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("k1").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("k1").generate();
    }

    final AtomicInteger turnstileCalls = new AtomicInteger();
    boolean turnstileOk = true;

    GatewayConfig cfg(boolean dev) {
        return new GatewayConfig(8080, "127.0.0.1", 4444, 30564, List.of(), "secret", dev, null, null, ISS,
            List.of(WEB, SMOKE), "ts", Map.of(), null, false, "dev", null, null, null);
    }

    Auth auth(boolean dev) {
        GatewayConfig c = cfg(dev);
        return new Auth(c, (t, ip) -> {
            turnstileCalls.incrementAndGet();
            return turnstileOk;
        }, Auth.processor(c, new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK()))));
    }

    JWTClaimsSet.Builder claims() {
        Date now = new Date();
        return new JWTClaimsSet.Builder().issuer(ISS).audience(WEB).subject("user-1").claim("token_use", "id")
            .issueTime(now).expirationTime(new Date(now.getTime() + 3_600_000));
    }

    String sign(JWTClaimsSet c, RSAKey k) throws Exception {
        SignedJWT j = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), c);
        j.sign(new RSASSASigner(k));
        return j.serialize();
    }

    In.Hello token(String t) {
        return new In.Hello("nick", null, t, null);
    }

    void rejected(Auth a, In.Hello h) {
        Unauthorised e = assertThrows(Unauthorised.class, () -> a.authenticate(h, "1.2.3.4"));
        if (h.idToken() != null) assertFalse(e.getMessage().contains(h.idToken()));
    }

    @Test
    void validTokenIsRegistered() throws Exception {
        Identity id = auth(false).authenticate(token(sign(claims().build(), key)), "1.2.3.4");
        assertEquals("user-1", id.sub());
        assertEquals(Identity.Level.REGISTERED, id.level());
        assertEquals(16, id.ipHash().length());
    }

    @Test
    void smokeClientAudienceAccepted() throws Exception {
        Identity id = auth(false).authenticate(token(sign(claims().audience(SMOKE).build(), key)), "1.2.3.4");
        assertEquals(Identity.Level.REGISTERED, id.level());
    }

    @Test
    void adminGroupIsAdmin() throws Exception {
        String t = sign(claims().claim("cognito:groups", List.of("other", "admin")).build(), key);
        assertEquals(Identity.Level.ADMIN, auth(false).authenticate(token(t), "1.2.3.4").level());
    }

    @Test
    void otherGroupIsRegistered() throws Exception {
        String t = sign(claims().claim("cognito:groups", List.of("other")).build(), key);
        assertEquals(Identity.Level.REGISTERED, auth(false).authenticate(token(t), "1.2.3.4").level());
    }

    @Test
    void badTokensRejected() throws Exception {
        Auth a = auth(false);
        rejected(a, token(sign(claims().expirationTime(new Date(System.currentTimeMillis() - 600_000)).build(), key)));
        rejected(a, token(sign(claims().audience("someone-else").build(), key)));
        rejected(a, token(sign(claims().claim("token_use", "access").build(), key)));
        rejected(a, token(sign(claims().issuer("https://evil.example/pool").build(), key)));
        rejected(a, token(sign(claims().build(), otherKey)));
        rejected(a, token(sign(claims().subject(null).build(), key)));
        rejected(a, token(sign(claims().issueTime(null).build(), key)));
        rejected(a, token("not-a-jwt"));
    }

    @Test
    void noneAlgorithmRejected() {
        rejected(auth(false), token(new PlainJWT(claims().build()).serialize()));
    }

    @Test
    void hmacAlgorithmRejected() throws Exception {
        SignedJWT j = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("k1").build(), claims().build());
        j.sign(new MACSigner(new byte[32]));
        rejected(auth(false), token(j.serialize()));
    }

    @Test
    void devIdentityOnlyInDevMode() throws Exception {
        In.Hello h = new In.Hello("n", null, null, new In.Dev("dev-1", List.of("admin")));
        rejected(auth(false), h);
        Identity id = auth(true).authenticate(h, "1.2.3.4");
        assertEquals("dev-1", id.sub());
        assertEquals(Identity.Level.ADMIN, id.level());
        assertEquals(Identity.Level.REGISTERED,
            auth(true).authenticate(new In.Hello("n", null, null, new In.Dev("d", List.of())), "1.2.3.4").level());
    }

    @Test
    void turnstileGuest() throws Exception {
        Identity id = auth(false).authenticate(new In.Hello("n", "tok", null, null), "1.2.3.4");
        assertEquals(Identity.Level.GUEST, id.level());
        assertEquals("guest:" + id.ipHash(), id.sub());
        assertEquals(1, turnstileCalls.get());
    }

    @Test
    void turnstileFailureRejected() {
        turnstileOk = false;
        rejected(auth(false), new In.Hello("n", "tok", null, null));
    }

    @Test
    void noCredentialsRejected() {
        rejected(auth(false), new In.Hello("n", null, null, null));
    }

    @Test
    void ipHashIsStableKeyedAndSixteenHex() {
        Auth a = auth(false);
        assertEquals(a.ipHash("1.2.3.4"), a.ipHash("1.2.3.4"));
        assertNotEquals(a.ipHash("1.2.3.4"), a.ipHash("1.2.3.5"));
        assertTrue(a.ipHash("1.2.3.4").matches("[0-9a-f]{16}"));
        assertNotEquals(a.ipHash("1.2.3.4"), auth(true).ipHash("1.2.3.4"), "dev uses the constant key");
    }
}
