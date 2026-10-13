package au.edu.uwa.mech.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Cloudflare Turnstile verification. Any error or timeout counts as failure. */
@FunctionalInterface
public interface Turnstile {
    String SITEVERIFY = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    int MAX_BODY = 16 * 1024;

    boolean verify(String token, String remoteIp);

    static Turnstile http(String secret) {
        return http(secret, SITEVERIFY);
    }

    static Turnstile http(String secret, String endpoint) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        ObjectMapper mapper = new ObjectMapper();
        return (token, ip) -> {
            try {
                String body = "secret=" + enc(secret) + "&response=" + enc(token) + "&remoteip=" + enc(ip);
                HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HttpResponse<java.io.InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                try (java.io.InputStream in = resp.body()) {
                    if (resp.statusCode() != 200) return false;
                    byte[] buf = in.readNBytes(MAX_BODY + 1);
                    if (buf.length > MAX_BODY) return false;
                    JsonNode success = mapper.readTree(buf).get("success");
                    return success != null && success.isBoolean() && success.booleanValue();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                return false;
            }
        };
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
