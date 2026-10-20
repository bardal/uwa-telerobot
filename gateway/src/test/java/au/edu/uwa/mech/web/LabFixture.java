package au.edu.uwa.mech.web;

import au.edu.uwa.mech.sim.Block;
import au.edu.uwa.mech.sim.SteppedTime;
import au.edu.uwa.mech.sim.World;
import au.edu.uwa.mech.web.dev.InProcessLab;
import java.util.Map;
import java.util.Optional;

/**
 * The whole lab in this JVM on free ports: {@link InProcessLab}. One per JVM, shared by every test
 * (and by {@code Main --dev --lab-in-process} run inside the test JVM). The arm moves in stepped time:
 * a move takes no wall-clock time, and {@link #motion()} can hold it mid-move.
 */
public final class LabFixture {
    private static LabFixture shared;

    private static final SteppedTime MOTION = new SteppedTime();

    private final InProcessLab lab;

    private LabFixture(InProcessLab lab) {
        this.lab = lab;
    }

    /** The lab for this JVM, started on first use. */
    public static synchronized LabFixture shared() throws Exception {
        if (shared == null) shared = new LabFixture(InProcessLab.shared(InProcessLab.Ports.free(), true, MOTION));
        return shared;
    }

    public int momPort() { return lab.momPort(); }

    public int adminPort() { return lab.adminPort(); }

    public InProcessLab.Ports ports() { return lab.ports(); }

    public World world() { return lab.world(); }

    /** The arm's clock: hold it to stop a move where it is, release it to let the move finish. */
    public SteppedTime motion() { return MOTION; }

    /** One of the simulated cameras, as the camera server sees it: every photo it takes reaches its image listeners. */
    public au.edu.uwa.mech.devices.camera.Camera camera(String name) throws Exception {
        return lab.cameras().getCamera(name);
    }

    /** PINs of the registered and admin accounts, keyed as {@link GatewayConfig#pins()}. */
    public Map<String, String> gatewayPins() { return lab.gatewayPins(); }

    public Optional<Block> block(String name) { return lab.block(name); }
}
