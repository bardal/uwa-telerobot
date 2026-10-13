package au.edu.uwa.mech.web;

/** One browser WebSocket, as seen by a {@link SessionHandler}. */
public interface ClientConnection {

    /** The client's address: the CloudFront viewer address when the origin secret was valid, else the socket peer. */
    String remoteIp();

    /** Queue a JSON text frame. Safe from any thread; silently dropped if the socket has closed. */
    void send(Out message);

    /** Queue a binary frame (the JPEG after an {@code image} message). */
    void sendBinary(byte[] data);

    /** Close with a WebSocket close code after queued frames have been sent. */
    void close(int code, String reason);

    boolean isOpen();

    /** Free slot for the handler's per-session state. */
    Object attachment();

    void attach(Object state);
}
