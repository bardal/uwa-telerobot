package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import au.edu.uwa.mech.web.cloud.MemoryParamStore;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The gateway's line to the lab's admin port, without a lab. */
class LabAdminTest {

    private static SessionRegistry registryFor(int adminPort) {
        GatewayConfig cfg = new GatewayConfig(0, "127.0.0.1", 1, adminPort,
            List.of("http://localhost"), null, true, null, null, null, List.of(), null, java.util.Map.of(), null,
            false, "test", null, null, null);
        return new SessionRegistry(cfg, new Auth(cfg, (token, ip) -> true, null), new Bans(new MemoryParamStore()),
            new Idle(System::currentTimeMillis, () -> {}), System::currentTimeMillis);
    }

    @Test
    void aLabThatIsNotListeningGivesNoReply() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = s.getLocalPort();
        }
        SessionRegistry registry = registryFor(port);
        try {
            assertNull(registry.labAdmin("blocks add"));
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void theLabsAnswerComesBackAsItIs() throws Exception {
        try (ServerSocket lab = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread fake = new Thread(() -> {
                try (Socket s = lab.accept()) {
                    String line = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII)).readLine();
                    s.getOutputStream().write(("blocks add".equals(line) ? "error the table is full (20 blocks)\n" : "error ?\n")
                        .getBytes(StandardCharsets.US_ASCII));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            fake.start();
            SessionRegistry registry = registryFor(lab.getLocalPort());
            try {
                assertEquals("error the table is full (20 blocks)", registry.labAdmin("blocks add"));
            } finally {
                registry.shutdown();
            }
            fake.join();
        }
    }
}
