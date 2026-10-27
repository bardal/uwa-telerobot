package au.edu.uwa.mech.web.dev;

import au.edu.uwa.mech.devices.camera.CameraManagerClient;
import au.edu.uwa.mech.devices.camera.SimCameraManager;
import au.edu.uwa.mech.devices.robot.Client;
import au.edu.uwa.mech.mom.MOMListener;
import au.edu.uwa.mech.mom.SessionRoot;
import au.edu.uwa.mech.mom.server.MOMServer;
import au.edu.uwa.mech.sim.Block;
import au.edu.uwa.mech.sim.MotionTime;
import au.edu.uwa.mech.sim.VirtualLab;
import au.edu.uwa.mech.sim.VirtualRobotServer;
import au.edu.uwa.mech.sim.World;
import au.edu.uwa.mech.web.GatewayConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * The whole lab in this JVM, as start.sh runs it as separate processes: the MOM server, the
 * VirtualLab (simulated arm and its three cameras) and RobotProxy. Everything binds 127.0.0.1,
 * with temporary config files: a users.ini holding guest, registered, admin, robot and camera
 * (random PINs for this run), and MOM.xml, lab.xml and RobotProxy.properties pointing at the ports.
 *
 * <p>Dev mode only ({@code --dev --lab-in-process}) and tests. RobotProxy keeps its ports in static
 * fields and the 2000 servers have no stop method, so there is at most one lab per JVM: a second
 * {@link #shared} returns the first, whatever ports it asked for.
 */
public final class InProcessLab {
    private static InProcessLab shared;

    /** MOM's port (its bulk port is the next one), the robot's control and position ports, the admin port. */
    public record Ports(int mom, int control, int position, int admin) {
        /** The 2000 lab's ports: MOM 4444, robot 30562 and 30563, admin 30564. */
        public static Ports standard() {
            return new Ports(4444, 30562, 30563, 30564);
        }

        /** Free ports, all distinct (and MOM's port + 1 free too). */
        public static Ports free() throws IOException {
            // hold every socket until all are chosen, so no two ports (nor MOM's port + 1) coincide
            List<ServerSocket> held = new ArrayList<>();
            try {
                int mom = freePortPair(held);
                return new Ports(mom, freePort(held), freePort(held), freePort(held));
            } finally {
                for (ServerSocket s : held) s.close();
            }
        }
    }

    private final Path dir;
    private final Ports ports;
    private final Map<String, String> pins;
    private World world;
    private SimCameraManager cameras;
    private au.edu.uwa.mech.devices.camera.Server cameraServer; // kept reachable

    /**
     * The lab for this JVM, started on first use. {@code fast} speeds the arm up (1500 mm/s), so a
     * pick-and-place takes a few seconds; it applies only to the first call.
     */
    public static synchronized InProcessLab shared(Ports ports, boolean fast) throws Exception {
        return shared(ports, fast, MotionTime.REAL);
    }

    /** As {@link #shared(Ports, boolean)}, with the arm moving in {@code time} (tests step it). */
    public static synchronized InProcessLab shared(Ports ports, boolean fast, MotionTime time) throws Exception {
        if (shared == null) {
            InProcessLab lab = new InProcessLab(ports);
            lab.start(fast, time);
            shared = lab;
        }
        return shared;
    }

    private InProcessLab(Ports ports) throws IOException {
        this.ports = ports;
        dir = Files.createTempDirectory("telerobot-lab");
        pins = Map.of(
            GatewayConfig.PIN_REGISTERED, randomPin(),
            GatewayConfig.PIN_ADMIN, randomPin(),
            "robot", randomPin(),
            "camera", randomPin());
    }

    public int momPort() { return ports.mom(); }

    public int adminPort() { return ports.admin(); }

    public Ports ports() { return ports; }

    public World world() { return world; }

    /** The simulated cameras the camera server publishes (tests watch their photos). */
    public au.edu.uwa.mech.devices.camera.CameraManager cameras() { return cameras; }

    /** PINs of the registered and admin accounts, keyed as {@link GatewayConfig#pins()}. */
    public Map<String, String> gatewayPins() {
        return Map.of(GatewayConfig.PIN_REGISTERED, pins.get(GatewayConfig.PIN_REGISTERED),
            GatewayConfig.PIN_ADMIN, pins.get(GatewayConfig.PIN_ADMIN));
    }

    public Optional<Block> block(String name) {
        return world.snapshot().blocks.stream().filter(b -> b.name.equals(name)).findFirst();
    }

    private void start(boolean fast, MotionTime time) throws Exception {
        // the 2000 servers write logs/users.log and logs/RobotCommands.log under the working directory
        Files.createDirectories(Path.of("logs"));
        Path users = write("users.ini", usersIni());
        Path momXml = write("MOM.xml", "<?xml version=\"1.0\"?>\n<config>\n  <usermanager>\n    <file>"
            + slashes(users) + "</file>\n  </usermanager>\n</config>\n");
        Path labXml = write("lab.xml", "<?xml version=\"1.0\"?>\n<config>\n  <robot>\n    <controlport>" + ports.control()
            + "</controlport>\n    <positionport>" + ports.position() + "</positionport>\n    <adminPort>" + ports.admin()
            + "</adminPort>\n  </robot>\n  <MOM>\n    <url>" + url("camera", pins.get("camera"), "camera")
            + "</url>\n  </MOM>\n</config>\n");
        Properties proxy = new Properties();
        proxy.setProperty("MOMurl", url("robot", pins.get("robot"), "robot"));
        proxy.setProperty("RobotServerHost", "127.0.0.1");
        proxy.setProperty("ControlPort", Integer.toString(ports.control()));
        proxy.setProperty("PositionPort", Integer.toString(ports.position()));
        Path proxyProps = dir.resolve("RobotProxy.properties");
        try (OutputStream out = Files.newOutputStream(proxyProps)) {
            proxy.store(out, "RobotProxy for the in-process lab");
        }

        if (fast) {
            // each waypoint still takes at least 0.3 s
            VirtualRobotServer.SPEED_MM_PER_S = 1500;
            VirtualRobotServer.ROTATION_DEG_PER_S = 900;
            VirtualRobotServer.GRIPPER_SECONDS = 0.1;
        }

        MOMServer mom = new PortedMomServer(momXml.toString(), ports.mom());
        mom.listen();

        world = World.withDefaultBlocks();
        VirtualRobotServer robot = new VirtualRobotServer(world, time);
        robot.start(ports.control(), ports.position());
        robot.serveAdmin(ports.admin());

        cameras = new SimCameraManager(world, VirtualLab.defaultCameras(), null);
        for (int attempt = 1; cameraServer == null; attempt++) {
            try {
                cameraServer = new au.edu.uwa.mech.devices.camera.Server(labXml.toString(), cameras);
            } catch (Exception e) {
                if (attempt >= 20) throw e;
                Thread.sleep(250);
            }
        }
        robot.onBlocksChanged(cameraServer::takePhotos);

        au.edu.uwa.mech.devices.robot.Server.main(new String[] {proxyProps.toString()});
        awaitReady();
    }

    /** A probe login that sees the robot (with a position) and all three cameras with a photo. */
    private void awaitReady() throws Exception {
        SessionRoot root = new SessionRoot(url("guest", "-1", "lab-probe"));
        Client client = new Client(root);
        CameraManagerClient cams = new CameraManagerClient(root);
        root.login();
        long end = System.currentTimeMillis() + 30_000;
        try {
            while (true) {
                int photos = 0;
                for (var e = cams.cameras(); e.hasMoreElements(); ) {
                    if (((au.edu.uwa.mech.devices.camera.Camera) e.nextElement()).lastPhoto() != null) photos++;
                }
                boolean robotUp = client.hasController() || client.getState().pose.position.x != 0;
                if (photos == 3 && robotUp) return;
                if (System.currentTimeMillis() > end) {
                    throw new IllegalStateException("lab not ready: photos=" + photos + " robot=" + robotUp);
                }
                Thread.sleep(100);
            }
        } finally {
            Thread logout = new Thread(() -> {
                try {
                    root.logout();
                } catch (Exception ignored) {
                    // the probe is finished either way
                }
            }, "lab-probe-logout");
            logout.setDaemon(true);
            logout.start();
        }
    }

    /** MOMServer listens on 4444/4445 unless told otherwise; its listener takes the port pair from "port". */
    private static final class PortedMomServer extends MOMServer {
        PortedMomServer(String configFile, int port) throws IOException {
            super(configFile);
            for (Object l : listeners) ((MOMListener) l).configure("port", Integer.valueOf(port));
        }
    }

    private String usersIni() {
        return "[guest]\nname=Guest\nPIN=-1\nUserLevel=Guest\n\n"
            + "[registered]\nname=Registered users\nPIN=" + pins.get(GatewayConfig.PIN_REGISTERED) + "\nUserLevel=Registered\n\n"
            + "[admin]\nname=Administrators\nPIN=" + pins.get(GatewayConfig.PIN_ADMIN) + "\nUserLevel=Team\n\n"
            + "[robot]\nname=Robot proxy\nPIN=" + pins.get("robot") + "\nUserLevel=Registered\n\n"
            + "[camera]\nname=Camera server\nPIN=" + pins.get("camera") + "\nUserLevel=Registered\n";
    }

    private String url(String user, String pin, String nick) {
        return "moms://" + user + ":" + pin + ":" + nick + "@127.0.0.1:" + ports.mom() + "/";
    }

    private Path write(String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.ISO_8859_1);
        return p;
    }

    private static String slashes(Path p) {
        return p.toAbsolutePath().toString().replace('\\', '/');
    }

    private static String randomPin() {
        byte[] b = new byte[8];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static int freePort(List<ServerSocket> held) throws IOException {
        ServerSocket s = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        held.add(s);
        return s.getLocalPort();
    }

    /** A free port whose successor is free too (MOM's bulk port is port + 1). */
    private static int freePortPair(List<ServerSocket> held) throws IOException {
        for (int i = 0; i < 50; i++) {
            int p = freePort(held);
            if (p >= 65535) continue;
            try {
                held.add(new ServerSocket(p + 1, 1, InetAddress.getByName("127.0.0.1")));
                return p;
            } catch (IOException taken) {
                // try another
            }
        }
        throw new IOException("no free port pair");
    }
}
