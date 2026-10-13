package au.edu.uwa.mech.sim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Stands in for RobotServer.exe and the ABB S4 controller, speaking the
 * protocol RobotProxy (au.edu.uwa.mech.devices.robot.Server) expects:
 * <ul>
 * <li>control port: one "gbh move &lt;script&gt;" or "gbh pos" per line; the reply
 *     is a Result line <code>major minor "text"</code> (1 = OK, 4 = error)
 *     followed by a blank line</li>
 * <li>position port: a stream of pose lines,
 *     <code>x y z 1 spin tilt gripper coordtype proximity Moving|Stopped</code></li>
 * </ul>
 * Moves are interpolated in time through the {@link World}.
 */
public class VirtualRobotServer
{
  public static double SPEED_MM_PER_S = 150;
  public static double ROTATION_DEG_PER_S = 90;
  public static double GRIPPER_SECONDS = 0.8;
  /** Workspace limits per axis, as RobotServer.xml's minZ and the arm's reach. */
  public static double[][] LIMITS = {
      {-150, 650}, {-150, 650}, {5, 450}, {-270, 90}, {-60, 60}};
  private static final String AXES = "xyzst";

  private final World world;
  private final Object motionLock = new Object();
  private InetAddress bound;
  /** Set by {@link #resetWorld()} to make a move in progress stop where it is. */
  private volatile boolean abort;

  private static final class MoveAborted extends RuntimeException
  {
    MoveAborted() { super("move aborted", null, false, false); }
  }

  private void checkAbort()
  {
    if (abort)
      throw new MoveAborted();
  }

  /**
   * Stop any move in progress (it replies with an error) and put the world back to the
   * start. Does not wait for the rest of a long script, only for the current step to stop.
   */
  public void resetWorld()
  {
    abort = true;
    synchronized (motionLock) {
      abort = false;
      world.reset();
    }
  }

  /** The address the servers listen on: -Dtelerobot.bind, default loopback only. */
  static InetAddress bindAddress() throws IOException
  {
    return InetAddress.getByName(System.getProperty("telerobot.bind", "127.0.0.1"));
  }

  /** The address the control socket is bound to (after {@link #start}). */
  public InetAddress boundTo() { return bound; }

  public VirtualRobotServer(World world) { this.world = world; }

  public void start(int controlPort, int positionPort) throws IOException
  {
    final ServerSocket control = new ServerSocket(controlPort, 5, bindAddress());
    final ServerSocket position = new ServerSocket(positionPort, 5, bindAddress());
    bound = control.getInetAddress();
    daemon("robot-control-accept", () -> acceptLoop(control, true));
    daemon("robot-position-accept", () -> acceptLoop(position, false));
    System.out.println("VirtualRobot: control port " + controlPort + ", position port " + positionPort);
  }

  /** Admin port: a line "reset" puts the world back to the start and is answered "ok". */
  public void serveAdmin(int port) throws IOException
  {
    final ServerSocket admin = new ServerSocket(port, 5, bindAddress());
    daemon("robot-admin-accept", () -> {
      while (true) {
        try {
          final Socket s = admin.accept();
          daemon("robot-admin", () -> serveAdminClient(s));
        } catch (IOException e) {
          System.err.println("VirtualRobot: admin accept failed: " + e);
          return;
        }
      }
    });
    System.out.println("VirtualRobot: admin port " + port);
  }

  private void serveAdminClient(Socket s)
  {
    try (Socket sock = s;
         BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1))) {
      OutputStream out = sock.getOutputStream();
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.equals("reset")) {
          resetWorld();
          System.out.println("VirtualRobot: world reset");
          out.write("ok\n".getBytes(StandardCharsets.ISO_8859_1));
        } else if (!line.isEmpty()) {
          out.write("error unknown command\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        out.flush();
      }
    } catch (IOException e) {
      System.out.println("VirtualRobot: admin client gone: " + e.getMessage());
    }
  }

  private void acceptLoop(ServerSocket ss, boolean control)
  {
    while (true) {
      try {
        final Socket s = ss.accept();
        System.out.println("VirtualRobot: " + (control ? "control" : "position") + " connection from "
                           + s.getRemoteSocketAddress());
        daemon("robot-client", () -> {
          if (control)
            serveControl(s);
          else
            servePosition(s);
        });
      } catch (IOException e) {
        System.err.println("VirtualRobot: accept failed: " + e);
        return;
      }
    }
  }

  private void serveControl(Socket s)
  {
    try (Socket sock = s;
         BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1))) {
      OutputStream out = sock.getOutputStream();
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty())
          continue;
        System.out.println("VirtualRobot: command " + line);
        String reply = execute(line);
        System.out.println("VirtualRobot: reply   " + reply);
        out.write((reply + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
      }
    } catch (IOException e) {
      System.out.println("VirtualRobot: control client gone: " + e.getMessage());
    }
  }

  private void servePosition(Socket s)
  {
    final Object signal = new Object();
    World.Listener l = w -> { synchronized (signal) { signal.notifyAll(); } };
    world.addListener(l);
    String last = null;
    try (Socket sock = s) {
      OutputStream out = sock.getOutputStream();
      while (true) {
        String now = world.getPose().toPositionLine(world.isMoving());
        if (!now.equals(last)) {
          out.write((now + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
          out.flush();
          last = now;
        }
        synchronized (signal) { signal.wait(1000); }
      }
    } catch (IOException | InterruptedException e) {
      System.out.println("VirtualRobot: position client gone: " + e.getMessage());
    }
  }

  /** Execute one command line, returning the Result line. */
  public String execute(String line)
  {
    String[] words = line.split("\\s+", 3);
    if (words.length < 2 || !words[0].equalsIgnoreCase("gbh"))
      return "4 1 \"language not recognised :" + words[0] + "\"";
    if (words[1].equalsIgnoreCase("pos"))
      return "1 0 \"Position follows\" " + world.getPose().toPositionLine(world.isMoving());
    if (!words[1].equalsIgnoreCase("move"))
      return "4 1 \"command not recognised :" + words[1] + "\"";
    synchronized (motionLock) { // one move at a time, like the real controller
      List<RobotPose> path = PathScript.parse(words.length > 2 ? words[2] : "", world.getPose());
      for (RobotPose p : path) {
        String err = check(p);
        if (err != null)
          return "4 2 \"" + err + "\"";
      }
      try {
        for (RobotPose p : path)
          moveTo(p);
      } catch (InterruptedException e) {
        return "4 3 \"move interrupted\"";
      } catch (MoveAborted e) {
        return "4 9 \"Move aborted by lab reset\"";
      } finally {
        world.setPose(world.getPose(), false);
      }
      return "1 0 \"Move completed\"";
    }
  }

  static String check(RobotPose p)
  {
    for (int i = 0; i < AXES.length(); i++) {
      char a = AXES.charAt(i);
      double v = p.get(a);
      if (v < LIMITS[i][0] || v > LIMITS[i][1])
        return a + "=" + Math.round(v) + " is outside the workspace [" + Math.round(LIMITS[i][0]) + ", "
            + Math.round(LIMITS[i][1]) + "]";
    }
    return null;
  }

  private void moveTo(RobotPose target) throws InterruptedException
  {
    RobotPose start = world.getPose();
    double dist = Math.sqrt(sq(target.x - start.x) + sq(target.y - start.y) + sq(target.z - start.z));
    double rot = Math.max(Math.abs(target.spin - start.spin), Math.abs(target.tilt - start.tilt));
    double duration = Math.max(Math.max(dist / SPEED_MM_PER_S, rot / ROTATION_DEG_PER_S), 0.3);
    long t0 = System.nanoTime();
    while (true) {
      checkAbort();
      double f = Math.min((System.nanoTime() - t0) / 1e9 / duration, 1);
      double e = f * f * (3 - 2 * f); // ease in and out
      world.setPose(new RobotPose(lerp(start.x, target.x, e), lerp(start.y, target.y, e), lerp(start.z, target.z, e),
                                  lerp(start.spin, target.spin, e), lerp(start.tilt, target.tilt, e), start.open), true);
      if (f >= 1)
        break;
      Thread.sleep(40);
    }
    if (target.open != start.open)
      moveGripper(target.open);
  }

  private void moveGripper(boolean open) throws InterruptedException
  {
    RobotPose p = world.getPose();
    if (open)
      world.opened();
    double from = world.snapshot().fingerGap;
    double to = open ? World.OPEN_GAP : world.closedGap();
    long t0 = System.nanoTime();
    while (true) {
      checkAbort();
      double f = Math.min((System.nanoTime() - t0) / 1e9 / GRIPPER_SECONDS, 1);
      world.setFingerGap(lerp(from, to, f));
      if (f >= 1)
        break;
      Thread.sleep(40);
    }
    checkAbort();
    world.setPose(p.withGripper(open), true);
    if (!open)
      world.closed();
  }

  private static double lerp(double a, double b, double f) { return a + (b - a) * f; }

  private static double sq(double d) { return d * d; }

  static void daemon(String name, Runnable r)
  {
    Thread t = new Thread(r, name);
    t.setDaemon(true);
    t.start();
  }
}
