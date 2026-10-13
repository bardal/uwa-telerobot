package au.edu.uwa.mech.web.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import au.edu.uwa.mech.web.GatewayConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The AWS start-up path: settings from the systemd unit's environment, secrets from SSM. */
class CloudConfigTest {
    static final String[] UNIT_ARGS = {"--port", "8080", "--mom-host", "127.0.0.1", "--mom-port", "4444",
        "--lab-admin-port", "30564"};

    static Map<String, String> unitEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("ALLOWED_ORIGIN", "https://d123.cloudfront.net");
        env.put("COGNITO_ISSUER", "https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_abc");
        env.put("COGNITO_CLIENT_IDS", "webclient,smokeclient");
        env.put("ARCHIVE_BUCKET", "telerobot-archive");
        env.put("AWS_REGION", "eu-west-2");
        env.put("ORIGIN_SECRET_PARAM", "/telerobot/origin-secret");
        env.put("TURNSTILE_SECRET_PARAM", "/telerobot/turnstile-secret");
        return env;
    }

    static MemoryParamStore fullStore() {
        MemoryParamStore s = new MemoryParamStore();
        s.put("/telerobot/origin-secret", "origin-123");
        s.put("/telerobot/turnstile-secret", "turnstile-456");
        s.put("/telerobot/pins/registered", "reg-pin-789012");
        s.put("/telerobot/pins/admin", "adm-pin-345678");
        s.put("/telerobot/bans", "[]");
        s.put("/telerobot/archive-bytes", "1234");
        return s;
    }

    @Test
    void theUnitsEnvironmentMeansAws() {
        GatewayConfig c = GatewayConfig.parse(UNIT_ARGS, unitEnv());
        assertTrue(CloudConfig.onAws(c));
        assertTrue(c.instanceStop(), "self-stop is on by default outside dev");
        assertFalse(CloudConfig.onAws(GatewayConfig.parse(new String[0], Map.of())));
        assertFalse(GatewayConfig.parse(new String[] {"--no-self-stop"}, unitEnv()).instanceStop());
        assertFalse(GatewayConfig.parse(new String[] {"--dev"}, Map.of()).instanceStop());
    }

    @Test
    void secretsAndPinsComeFromSsmAndTheRestFromTheEnvironment() {
        GatewayConfig c = CloudConfig.load(GatewayConfig.parse(UNIT_ARGS, unitEnv()), fullStore());
        assertEquals("origin-123", c.originSecret());
        assertEquals("turnstile-456", c.turnstileSecret());
        assertEquals(Map.of("registered", "reg-pin-789012", "admin", "adm-pin-345678"), c.pins());
        assertEquals(List.of("https://d123.cloudfront.net"), c.allowedOrigins());
        assertEquals("https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_abc", c.cognitoIssuer());
        assertEquals(List.of("webclient", "smokeclient"), c.cognitoClientIds());
        assertEquals("telerobot-archive", c.archiveBucket());
        assertEquals("eu-west-2", c.awsRegion());
        assertEquals(8080, c.port());
        assertEquals(4444, c.momPort());
        assertEquals(30564, c.labAdminPort());
        assertFalse(c.dev());
        assertEquals(1234, CloudConfig.archiveBytes(fullStore()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/telerobot/origin-secret", "/telerobot/turnstile-secret", "/telerobot/pins/registered",
        "/telerobot/pins/admin", "/telerobot/bans", "/telerobot/archive-bytes"})
    void aMissingOrBlankParameterStopsTheGatewayNamingIt(String name) {
        GatewayConfig c = GatewayConfig.parse(UNIT_ARGS, unitEnv());
        MemoryParamStore missing = withRemoved(name);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CloudConfig.load(c, missing));
        assertTrue(e.getMessage().contains(name), e.getMessage());

        MemoryParamStore blank = fullStore();
        blank.put(name, "  ");
        e = assertThrows(IllegalStateException.class, () -> CloudConfig.load(c, blank));
        assertTrue(e.getMessage().contains(name), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALLOWED_ORIGIN", "COGNITO_ISSUER", "COGNITO_CLIENT_IDS", "ARCHIVE_BUCKET",
        "ORIGIN_SECRET_PARAM", "TURNSTILE_SECRET_PARAM"})
    void aMissingEnvironmentVariableStopsTheGatewayNamingIt(String var) {
        Map<String, String> env = unitEnv();
        env.remove(var);
        GatewayConfig c = GatewayConfig.parse(UNIT_ARGS, env);
        assertTrue(CloudConfig.onAws(c), "either *_PARAM variable means AWS");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CloudConfig.load(c, fullStore()));
        assertTrue(e.getMessage().contains(var), e.getMessage());
    }

    @Test
    void oneParamVariableAloneStillMeansAwsAndTheOtherIsRequired() {
        Map<String, String> env = unitEnv();
        env.remove("TURNSTILE_SECRET_PARAM");
        GatewayConfig c = GatewayConfig.parse(UNIT_ARGS, env);
        assertTrue(CloudConfig.onAws(c));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CloudConfig.load(c, fullStore()));
        assertTrue(e.getMessage().contains("TURNSTILE_SECRET_PARAM"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORIGIN_SECRET_PARAM", "TURNSTILE_SECRET_PARAM", "ARCHIVE_BUCKET", "COGNITO_ISSUER"})
    void devRefusesToStartInTheAwsEnvironment(String var) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> GatewayConfig.parse(new String[] {"--dev"}, Map.of(var, "x")));
        assertTrue(e.getMessage().contains(var) && e.getMessage().contains("--dev"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> GatewayConfig.parse(new String[] {"--dev"}, unitEnv()));
        assertTrue(GatewayConfig.parse(new String[] {"--dev"}, Map.of(var, " ")).dev(), "blank counts as unset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"lots", "-5", "12.5", ""})
    void anUnreadableByteCounterStopsTheGateway(String value) {
        MemoryParamStore s = fullStore();
        s.put("/telerobot/archive-bytes", value);
        GatewayConfig c = GatewayConfig.parse(UNIT_ARGS, unitEnv());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CloudConfig.load(c, s));
        assertTrue(e.getMessage().contains("/telerobot/archive-bytes"), e.getMessage());
        if (!value.isEmpty()) assertThrows(IllegalStateException.class, () -> CloudConfig.archiveBytes(s));
        s.put("/telerobot/archive-bytes", " 42 ");
        assertEquals(42, CloudConfig.archiveBytes(s));
    }

    private static MemoryParamStore withRemoved(String name) {
        MemoryParamStore full = fullStore();
        MemoryParamStore s = new MemoryParamStore();
        for (String n : List.of("/telerobot/origin-secret", "/telerobot/turnstile-secret", "/telerobot/pins/registered",
            "/telerobot/pins/admin", "/telerobot/bans", "/telerobot/archive-bytes")) {
            if (!n.equals(name)) s.put(n, full.get(n).orElseThrow());
        }
        return s;
    }
}
