package au.edu.uwa.mech.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Decides whether a WebSocket handshake is let in: the Origin, the CDN secret header and the global
 * caps (60 viewers, 4 connections per IP, 10 handshakes a second). A successful {@link #check}
 * reserves a slot that the caller must give back with {@link #release} when the connection ends.
 */
public final class Admission {
    public static final String SECRET_HEADER = "X-Telerobot-Origin";
    public static final String VIEWER_ADDRESS_HEADER = "CloudFront-Viewer-Address";

    public static final int MAX_VIEWERS = 60;
    public static final int MAX_PER_IP = 4;
    public static final int MAX_HANDSHAKES_PER_SECOND = 10;

    public static final String BAD_SECRET = "bad-secret";
    public static final String BAD_ORIGIN = "bad-origin";
    public static final String HANDSHAKE_RATE = "too-many-handshakes";
    public static final String TOO_MANY_VIEWERS = "too-many-viewers";
    public static final String TOO_MANY_FROM_IP = "too-many-from-ip";

    private final GatewayConfig config;
    private final LongSupplier clockMillis;
    private final byte[] secret;
    private final ArrayDeque<Long> handshakes = new ArrayDeque<>();
    private final Map<String, Integer> perIp = new HashMap<>();
    private int viewers;

    public Admission(GatewayConfig config, LongSupplier clockMillis) {
        this.config = config;
        this.clockMillis = clockMillis;
        this.secret = config.originSecret() == null ? null : config.originSecret().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param header lookup of a request header by (case-insensitive) name, null if absent
     * @param socketIp the peer address of the TCP connection
     * @return empty if admitted (a slot is now reserved for {@link #resolveIp}), else the refusal reason
     */
    public synchronized Optional<String> check(Function<String, String> header, String socketIp) {
        long now = clockMillis.getAsLong();
        while (!handshakes.isEmpty() && now - handshakes.peekFirst() >= 1000) handshakes.pollFirst();
        boolean rateOk = handshakes.size() < MAX_HANDSHAKES_PER_SECOND;
        handshakes.addLast(now);
        if (!rateOk) return Optional.of(HANDSHAKE_RATE);

        boolean secretValid = secretValid(header);
        if (!secretValid && !(config.dev() && secret == null)) return Optional.of(BAD_SECRET);

        String origin = header.apply("Origin");
        if (origin == null || !config.allowedOrigins().contains(origin)) return Optional.of(BAD_ORIGIN);

        if (viewers >= MAX_VIEWERS) return Optional.of(TOO_MANY_VIEWERS);
        String ip = resolveIp(header, socketIp);
        if (perIp.getOrDefault(ip, 0) >= MAX_PER_IP) return Optional.of(TOO_MANY_FROM_IP);

        viewers++;
        perIp.merge(ip, 1, Integer::sum);
        return Optional.empty();
    }

    public synchronized void release(String ip) {
        Integer n = perIp.get(ip);
        if (n == null) return;
        if (n <= 1) perIp.remove(ip);
        else perIp.put(ip, n - 1);
        if (viewers > 0) viewers--;
    }

    public synchronized int viewers() {
        return viewers;
    }

    /** The client address: from CloudFront-Viewer-Address when the secret is valid, else the socket. */
    public String resolveIp(Function<String, String> header, String socketIp) {
        if (!secretValid(header)) return socketIp;
        String v = header.apply(VIEWER_ADDRESS_HEADER);
        if (v == null) return socketIp;
        v = v.trim();
        int colon = v.lastIndexOf(':');
        if (colon <= 0) return socketIp;
        String host = v.substring(0, colon);
        String port = v.substring(colon + 1);
        if (!port.matches("\\d{1,5}")) return socketIp;
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        // an IP literal only: hex digits, dots and colons (keeps log and map keys clean)
        if (host.isEmpty() || host.length() > 45 || !host.matches("[0-9a-fA-F.:]+")) return socketIp;
        return host;
    }

    private boolean secretValid(Function<String, String> header) {
        if (secret == null) return false;
        String given = header.apply(SECRET_HEADER);
        if (given == null) return false;
        return MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8));
    }
}
