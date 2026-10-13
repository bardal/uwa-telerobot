package au.edu.uwa.mech.web;

/** A client message that is not valid protocol. Carries a short reason that is safe to log. */
public final class BadMessage extends Exception {
    public BadMessage(String message) {
        super(message);
    }

    public BadMessage(String message, Throwable cause) {
        super(message, cause);
    }
}
