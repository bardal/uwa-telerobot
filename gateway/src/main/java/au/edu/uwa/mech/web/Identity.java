package au.edu.uwa.mech.web;

/** Who a session belongs to: a stable subject, a trust level and a short hash of the client IP. */
public record Identity(String sub, Level level, String ipHash) {
    public enum Level { GUEST, REGISTERED, ADMIN }
}
