package au.edu.uwa.mech.web;

import java.util.List;

/**
 * Placeholder until the MOM bridge (Task 5): accepts every hello as a guest and echoes a welcome.
 */
public final class StubSessionHandler implements SessionHandler {
    private final GatewayConfig config;

    public StubSessionHandler(GatewayConfig config) {
        this.config = config;
    }

    @Override
    public void onHello(ClientConnection connection, In.Hello hello) {
        connection.send(new Out.Welcome(hello.nick(), "guest", List.of(), config.commit(), new Out.Limits(400, 30, 300)));
    }

    @Override
    public void onMessage(ClientConnection connection, In message) {
        // nothing to do yet
    }

    @Override
    public void onClose(ClientConnection connection) {
        // nothing to do yet
    }
}
