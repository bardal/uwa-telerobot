package au.edu.uwa.mech.web;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Turns a hello into an {@link Identity}: Cognito ID token, Turnstile guest, or (dev only) a claimed identity. */
public final class Auth {
    private static final Logger LOG = LoggerFactory.getLogger(Auth.class);
    private static final int MAX_DEV_SUB = 64;
    private static final int CLOCK_SKEW_SECONDS = 60;
    private static final int HTTP_TIMEOUT_MS = 5000;

    private final GatewayConfig cfg;
    private final Turnstile turnstile;
    private final JWTProcessor<SecurityContext> jwt;

    public Auth(GatewayConfig cfg, Turnstile turnstile, JWTProcessor<SecurityContext> jwt) {
        if (!cfg.dev() && (cfg.originSecret() == null || cfg.originSecret().isBlank())) {
            throw new IllegalStateException("originSecret is required outside dev mode");
        }
        this.cfg = cfg;
        this.turnstile = turnstile;
        this.jwt = jwt;
    }

    /** Processor using the pool's remote JWKS (cached by nimbus, 5 s connect/read timeouts). */
    public static JWTProcessor<SecurityContext> remoteProcessor(GatewayConfig cfg) {
        if (cfg.cognitoIssuer() == null || cfg.cognitoIssuer().isBlank()) {
            throw new IllegalArgumentException("cognitoIssuer is not configured");
        }
        try {
            URI jwks = URI.create(cfg.cognitoIssuer().replaceAll("/+$", "") + "/.well-known/jwks.json");
            JWKSource<SecurityContext> source = JWKSourceBuilder.<SecurityContext>create(
                    jwks.toURL(), new DefaultResourceRetriever(HTTP_TIMEOUT_MS, HTTP_TIMEOUT_MS, 51200))
                .retrying(true)
                .build();
            return processor(cfg, source);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("bad cognito issuer", e);
        }
    }

    /** Processor for the given key source: RS256 only, with Cognito ID-token claim checks. */
    public static JWTProcessor<SecurityContext> processor(GatewayConfig cfg, JWKSource<SecurityContext> keys) {
        DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
        p.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        DefaultJWTClaimsVerifier<SecurityContext> v = new DefaultJWTClaimsVerifier<>(
            new java.util.HashSet<>(cfg.cognitoClientIds()),
            new JWTClaimsSet.Builder().issuer(cfg.cognitoIssuer()).claim("token_use", "id").build(),
            Set.of("exp", "iat", "sub", "iss", "aud", "token_use"),
            Set.of());
        v.setMaxClockSkew(CLOCK_SKEW_SECONDS);
        p.setJWTClaimsSetVerifier(v);
        return p;
    }

    public Identity authenticate(In.Hello h, String remoteIp) throws Unauthorised {
        String ipHash = ipHash(remoteIp);
        if (h.idToken() != null) return fromToken(h.idToken(), ipHash);
        if (h.dev() != null) {
            if (!cfg.dev()) throw new Unauthorised("dev identity not allowed");
            String dsub = h.dev().sub();
            if (dsub == null || dsub.isBlank() || dsub.length() > MAX_DEV_SUB || h.dev().groups() == null) {
                throw new Unauthorised("bad dev identity");
            }
            return new Identity(h.dev().sub(), level(h.dev().groups()), ipHash);
        }
        if (h.turnstile() != null) {
            if (remoteIp == null || remoteIp.isBlank()) throw new Unauthorised("no client address");
            if (!turnstile.verify(h.turnstile(), remoteIp)) throw new Unauthorised("turnstile failed");
            return new Identity("guest:" + ipHash, Identity.Level.GUEST, ipHash);
        }
        throw new Unauthorised("no credentials");
    }

    private Identity fromToken(String token, String ipHash) throws Unauthorised {
        if (cfg.cognitoIssuer() == null || cfg.cognitoClientIds().isEmpty()) {
            throw new Unauthorised("token login not configured");
        }
        try {
            JWTClaimsSet claims = jwt.process(token, null);
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) throw new Unauthorised("token has no subject");
            return new Identity(sub, level(claims.getStringListClaim("cognito:groups")), ipHash);
        } catch (Unauthorised e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("token rejected: {}", e.getClass().getSimpleName());
            throw new Unauthorised("invalid token");
        }
    }

    private static Identity.Level level(List<String> groups) {
        return groups != null && groups.contains("admin") ? Identity.Level.ADMIN : Identity.Level.REGISTERED;
    }

    String ipHash(String ip) {
        String key = cfg.dev() ? "dev" : cfg.originSecret();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] d = mac.doFinal((ip == null ? "" : ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
