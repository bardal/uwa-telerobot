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

    public static final long DEFAULT_LEASE_MILLIS = 10_000;

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
    private final java.util.Set<Reservation> pending = new java.util.HashSet<>();
    private final long leaseMillis;
    private int viewers;

    public Admission(GatewayConfig config, LongSupplier clockMillis) {
        this(config, clockMillis, DEFAULT_LEASE_MILLIS);
    }

    public Admission(GatewayConfig config, LongSupplier clockMillis, long leaseMillis) {
        this.config = config;
        this.clockMillis = clockMillis;
        this.leaseMillis = leaseMillis;
        this.secret = config.originSecret() == null ? null : config.originSecret().getBytes(StandardCharsets.UTF_8);
    }

    /** A reserved slot. Unconfirmed reservations are reclaimed by {@link #sweep()} after the lease. */
    public final class Reservation {
        private final String ip;
        private final long deadline;
        private boolean confirmed;
        private boolean released;

        private Reservation(String ip, long deadline) {
            this.ip = ip;
            this.deadline = deadline;
        }

        public String ip() { return ip; }

        /** The WebSocket opened. False if the lease already expired (the caller must close the socket). */
        public boolean confirm() {
            synchronized (Admission.this) {
                if (released) return false;
                confirmed = true;
                pending.remove(this);
                return true;
            }
        }

        /** Idempotent. */
        public void release() {
            synchronized (Admission.this) {
                if (released) return;
                released = true;
                pending.remove(this);
                Admission.this.release(ip);
            }
        }
    }

    /** Outcome of {@link #admit}: either a refusal reason or a leased reservation. */
    public record Admitted(String refusal, Reservation reservation) {}

    /**
     * Like {@link #check} but the slot is a lease: confirm it when the socket opens, or {@link #sweep()}
     * takes it back after the lease time.
     */
    public synchronized Admitted admit(Function<String, String> header, String socketIp) {
        Optional<String> refusal = decide(header, socketIp);
        if (refusal.isPresent()) return new Admitted(refusal.get(), null);
        String ip = resolveIp(header, socketIp);
        reserveSlot(ip);
        Reservation r = new Reservation(ip, clockMillis.getAsLong() + leaseMillis);
        pending.add(r);
        return new Admitted(null, r);
    }

    /** Releases reservations never confirmed within the lease. Returns how many. */
    public synchronized int sweep() {
        long now = clockMillis.getAsLong();
        int n = 0;
        for (Reservation r : java.util.List.copyOf(pending)) {
            if (now >= r.deadline) {
                r.release();
                n++;
            }
        }
        return n;
    }

    /**
     * Permanent-slot variant (no lease), kept for callers that release by IP.
     * @param header lookup of a request header by (case-insensitive) name, null if absent
     * @param socketIp the peer address of the TCP connection
     * @return empty if admitted (a slot is now reserved), else the refusal reason
     */
    public synchronized Optional<String> check(Function<String, String> header, String socketIp) {
        Optional<String> refusal = decide(header, socketIp);
        if (refusal.isEmpty()) {
            reserveSlot(resolveIp(header, socketIp));
        }
        return refusal;
    }

    private Optional<String> decide(Function<String, String> header, String socketIp) {
        // Only requests that carry the CDN secret count toward the handshake rate, so direct
        // probes of the origin cannot use up the allowance of real traffic.
        boolean secretValid = secretValid(header);
        if (!secretValid && !(config.dev() && secret == null)) return Optional.of(BAD_SECRET);

        long now = clockMillis.getAsLong();
        while (!handshakes.isEmpty() && now - handshakes.peekFirst() >= 1000) handshakes.pollFirst();
        boolean rateOk = handshakes.size() < MAX_HANDSHAKES_PER_SECOND;
        handshakes.addLast(now);
        if (!rateOk) return Optional.of(HANDSHAKE_RATE);

        String origin = header.apply("Origin");
        if (origin == null || !config.allowedOrigins().contains(origin)) return Optional.of(BAD_ORIGIN);

        if (viewers >= MAX_VIEWERS) return Optional.of(TOO_MANY_VIEWERS);
        String ip = resolveIp(header, socketIp);
        if (perIp.getOrDefault(ip, 0) >= MAX_PER_IP) return Optional.of(TOO_MANY_FROM_IP);
        return Optional.empty();
    }

    private void reserveSlot(String ip) {
        viewers++;
        perIp.merge(ip, 1, Integer::sum);
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
