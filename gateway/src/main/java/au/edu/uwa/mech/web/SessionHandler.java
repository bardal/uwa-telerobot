package au.edu.uwa.mech.web;

/**
 * What the gateway does once a client has said {@code hello}. The server owns the socket, the
 * framing, the strict parse and admission; an implementation (Task 5 wires the MOM bridge) owns
 * authentication of the hello, the session state and everything after it. Calls for one
 * connection are never concurrent with each other.
 */
public interface SessionHandler {

    /** The first message of a connection. Reply with {@code welcome} or an error and close. */
    void onHello(ClientConnection connection, In.Hello hello);

    /** Any later valid message. */
    void onMessage(ClientConnection connection, In message);

    /** A message that failed strict parsing, after hello. The default tells the client. */
    default void onBadMessage(ClientConnection connection, BadMessage problem) {
        connection.send(new Out.Error("bad-message", "malformed message"));
    }

    /** The connection has ended, for whatever reason. Called exactly once if {@code onHello} was. */
    void onClose(ClientConnection connection);
}
