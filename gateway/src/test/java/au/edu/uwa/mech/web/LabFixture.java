package au.edu.uwa.mech.web;

import au.edu.uwa.mech.sim.Block;
import au.edu.uwa.mech.sim.World;
import au.edu.uwa.mech.web.dev.InProcessLab;
import java.util.Map;
import java.util.Optional;

/**
 * The whole lab in this JVM on free ports, with the fast arm: {@link InProcessLab}. One per JVM,
 * shared by every test (and by {@code Main --dev --lab-in-process} run inside the test JVM).
 */
public final class LabFixture {
    private static LabFixture shared;

    private final InProcessLab lab;

    private LabFixture(InProcessLab lab) {
        this.lab = lab;
    }

    /** The lab for this JVM, started on first use. */
    public static synchronized LabFixture shared() throws Exception {
        if (shared == null) shared = new LabFixture(InProcessLab.shared(InProcessLab.Ports.free(), true));
        return shared;
    }

    public int momPort() { return lab.momPort(); }

    public int adminPort() { return lab.adminPort(); }

    public World world() { return lab.world(); }

    /** PINs of the registered and admin accounts, keyed as {@link GatewayConfig#pins()}. */
    public Map<String, String> gatewayPins() { return lab.gatewayPins(); }

    public Optional<Block> block(String name) { return lab.block(name); }
}
