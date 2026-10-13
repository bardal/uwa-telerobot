package au.edu.uwa.mech.web;

/** Authentication failed. The reason is short and never contains token contents. */
public final class Unauthorised extends Exception {
    public Unauthorised(String reason) {
        super(reason);
    }
}
