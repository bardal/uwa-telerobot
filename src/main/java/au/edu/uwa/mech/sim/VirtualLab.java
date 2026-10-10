package au.edu.uwa.mech.sim;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import au.edu.uwa.mech.devices.camera.SimCameraManager;
import au.edu.uwa.mech.util.XMLConfigFile;

/**
 * The virtual workcell in one process: the simulated arm and blocks
 * ({@link World}), served to RobotProxy as if it were RobotServer.exe
 * ({@link VirtualRobotServer}), and photographed by simulated cameras that
 * the original camera server ({@link au.edu.uwa.mech.devices.camera.Server})
 * publishes through MOM.
 *
 * <pre>
 * java au.edu.uwa.mech.sim.VirtualLab config/lab.xml          run the lab
 * java au.edu.uwa.mech.sim.VirtualLab --preview outdir         render each camera to PNG and exit
 * </pre>
 * The config file holds &lt;robot&gt;&lt;controlport/&gt;&lt;positionport/&gt;&lt;/robot&gt;,
 * &lt;MOM&gt;&lt;url/&gt;&lt;/MOM&gt; (the camera server's login) and optionally
 * &lt;images&gt;&lt;directory/&gt;&lt;/images&gt;; without &lt;MOM&gt; only the robot runs.
 */
public class VirtualLab
{
  /** The three cameras, named after the originals; image size 400x300. */
  public static List<VirtualCamera> defaultCameras()
  {
    List<VirtualCamera> cams = new ArrayList<VirtualCamera>();
    cams.add(new VirtualCamera("Colour", new double[] {300, 1180, 780}, new double[] {250, 200, 30}, 46, 400, 300));
    cams.add(new VirtualCamera("YCam", new double[] {1230, 330, 430}, new double[] {250, 210, 70}, 44, 400, 300));
    cams.add(new VirtualCamera("ZCam", new double[] {820, 760, 1350}, new double[] {240, 230, 0}, 40, 400, 300));
    return cams;
  }

  public static void main(String[] args) throws Exception
  {
    if (args.length == 2 && args[0].equals("--preview")) {
      preview(new File(args[1]));
      return;
    }
    if (args.length != 1) {
      System.err.println("usage: VirtualLab <config.xml> | --preview <outdir>");
      System.exit(2);
    }
    XMLConfigFile config = new XMLConfigFile(args[0]);
    World world = World.withDefaultBlocks();
    new VirtualRobotServer(world).start(config.getInt("robot", "controlport", 30562),
                                        config.getInt("robot", "positionport", 30563));
    String momURL = config.getString("MOM", "url", null);
    if (momURL == null) {
      System.out.println("VirtualLab: no <MOM><url> configured, running the robot only");
      synchronized (world) { while (true) world.wait(); }
    }
    String dir = config.getString("images", "directory", null);
    if (dir != null)
      new File(dir).mkdirs();
    SimCameraManager cameras = new SimCameraManager(world, defaultCameras(), dir);
    // the camera server logs into MOM itself; retry until MOM and the robot proxy are up
    au.edu.uwa.mech.devices.camera.Server server = null;
    for (int attempt = 1; server == null; attempt++) {
      try {
        server = new au.edu.uwa.mech.devices.camera.Server(args[0], cameras);
      } catch (Exception e) {
        System.out.println("VirtualLab: camera server could not start (" + e + "), retry " + attempt);
        Thread.sleep(3000);
      }
    }
    System.out.println("VirtualLab: cameras " + defaultCameras().size() + " publishing via " + momURL);
    server.runUntilShutdown();
  }

  /** Render every camera on a default world, plus one with the arm reaching for a block. */
  static void preview(File dir) throws Exception
  {
    dir.mkdirs();
    Renderer r = new Renderer();
    World w = World.withDefaultBlocks();
    for (VirtualCamera c : defaultCameras())
      ImageIO.write(r.render(c, w.snapshot(), "preview"), "png", new File(dir, c.name + ".png"));
    w.setPose(new RobotPose(120, 150, 20, -90, 0, true), false);
    for (VirtualCamera c : defaultCameras())
      ImageIO.write(r.render(c, w.snapshot(), "reach"), "png", new File(dir, c.name + "-reach.png"));
    System.out.println("wrote previews to " + dir);
  }
}
