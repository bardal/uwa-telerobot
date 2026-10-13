package au.edu.uwa.mech.web;

/** One browser WebSocket, as seen by a {@link SessionHandler}. */
public interface ClientConnection {

    /** The client's address: the CloudFront viewer address when the origin secret was valid, else the socket peer. */
    String remoteIp();

    /** Queue a JSON text frame. Safe from any thread; silently dropped if the socket has closed. */
    default void send(Out message) {
        send(message, () -> {});
    }

    /**
     * Queue a JSON text frame; {@code written} runs once, when it has been written or has failed
     * (including when the socket is already closed). It may run on the calling thread.
     */
    void send(Out message, Runnable written);

    /**
     * Queue an {@code image} header and its JPEG as one unit (nothing else between them as long as the
     * caller holds its own send lock); {@code written} runs once both are written or have failed.
     */
    void sendImage(Out.Image header, byte[] jpeg, Runnable written);

    /** Bytes queued for the socket and not yet written. */
    long queuedBytes();

    /** Frames queued for the socket and not yet written. */
    int queuedMessages();

    /**
     * Close with a WebSocket close code after queued frames have been sent. From then on nothing the
     * client sends is passed on, and a client that does not finish the close is disconnected.
     */
    void close(int code, String reason);

    boolean isOpen();

    /** Free slot for the handler's per-session state. */
    Object attachment();

    void attach(Object state);
}
