package au.edu.uwa.mech.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.Test;

public class WorldTest
{
  private static Block find(World w, String name)
  {
    for (Block b : w.snapshot().blocks)
      if (b.name.equals(name))
        return b;
    throw new AssertionError(name);
  }

  @Test
  public void pickUpAndStack()
  {
    World w = World.withDefaultBlocks();
    VirtualRobotServer robot = new VirtualRobotServer(w);
    VirtualRobotServer.SPEED_MM_PER_S = 5000;   // keep the test quick
    VirtualRobotServer.GRIPPER_SECONDS = 0.05;
    // start where a cursor move left the arm, tilted and with the gripper closed
    robot.execute("gbh move x186,y313,s-19,t-24,z387,gclosed");
    // the help page's example: red block onto the blue one
    String reply = robot.execute("gbh move x120,y150,z60,s-90,t0,g0;z15;g1;z80;x330,y120;z45;g0;z90");
    assertEquals("1 0 \"Move completed\"", reply);
    Block red = find(w, "red"), blue = find(w, "blue");
    assertEquals(330, red.getX(), 1e-6);
    assertEquals(120, red.getY(), 1e-6);
    assertEquals(blue.top(), red.getZ(), 1e-6);
  }

  @Test
  public void closingOnNothingLeavesBlocksAlone()
  {
    World w = World.withDefaultBlocks();
    VirtualRobotServer robot = new VirtualRobotServer(w);
    VirtualRobotServer.SPEED_MM_PER_S = 5000;
    VirtualRobotServer.GRIPPER_SECONDS = 0.05;
    robot.execute("gbh move x50,y50,z15;g1;z100");
    assertEquals(0, find(w, "red").getZ(), 0);
    assertEquals(0, w.snapshot().fingerGap, 0);
  }

  @Test
  public void badMovesAreRefusedWithAnError()
  {
    VirtualRobotServer robot = new VirtualRobotServer(World.withDefaultBlocks());
    assertTrue(robot.execute("gbh move z0").startsWith("4 "));
    assertTrue(robot.execute("rapid moveJ").startsWith("4 "));
    assertTrue(robot.execute("gbh pos").startsWith("1 0 \"Position follows\" 250 250 100"));
  }

  private static int freePort() throws Exception
  {
    try (ServerSocket ss = new ServerSocket(0)) {
      return ss.getLocalPort();
    }
  }

  @Test
  public void resetPutsEverythingBack()
  {
    World w = World.withDefaultBlocks();
    w.setPose(new RobotPose(10, 20, 30, -45, 10, false), false);
    w.setFingerGap(5);
    int[] fired = {0};
    w.addListener(x -> fired[0]++);
    w.reset();
    assertEquals(1, fired[0]);
    Block red = find(w, "red");
    assertEquals(120, red.getX(), 0);
    assertEquals(150, red.getY(), 0);
    assertEquals(0, red.getZ(), 0);
    assertEquals(5, w.snapshot().blocks.size());
    assertTrue(w.getPose().toPositionLine(false).startsWith("250 250 100"));
    assertTrue(w.getPose().open);
    assertEquals(World.OPEN_GAP, w.snapshot().fingerGap, 0);
  }

  @Test
  public void resetPutsMovedAndHeldBlocksBack()
  {
    World w = World.withDefaultBlocks();
    VirtualRobotServer robot = new VirtualRobotServer(w);
    VirtualRobotServer.SPEED_MM_PER_S = 5000;
    VirtualRobotServer.GRIPPER_SECONDS = 0.05;
    robot.execute("gbh move x120,y150,z60,s-90,t0,g0;z15;g1;z80;x330,y120;z45;g0;z90;x50,y50,z15;g1;z80");
    assertEquals(330, find(w, "red").getX(), 1e-6);
    w.reset();
    assertEquals(120, find(w, "red").getX(), 0);
    assertEquals(150, find(w, "red").getY(), 0);
    assertEquals(0, find(w, "red").getZ(), 0);
    w.setPose(new RobotPose(0, 0, 100, -90, 0, true), false);
    assertEquals(0, find(w, "red").getZ(), 0);   // no longer carried along
  }

  @Test
  public void serversBindToLoopbackOnly() throws Exception
  {
    int control = freePort(), position = freePort();
    VirtualRobotServer robot = new VirtualRobotServer(World.withDefaultBlocks());
    robot.start(control, position);
    try (Socket s = new Socket(InetAddress.getByName("127.0.0.1"), control)) {
      assertTrue(s.isConnected());
    }
    assertTrue(robot.boundTo().isLoopbackAddress());
    assertEquals(InetAddress.getByName("127.0.0.1"), robot.boundTo());
  }

  @Test
  public void adminPortResetsTheWorld() throws Exception
  {
    World w = World.withDefaultBlocks();
    w.setPose(new RobotPose(10, 20, 30, -45, 10, false), false);
    VirtualRobotServer robot = new VirtualRobotServer(w);
    int admin = freePort();
    robot.serveAdmin(admin);
    try (Socket s = new Socket(InetAddress.getByName("127.0.0.1"), admin)) {
      s.getOutputStream().write("reset\n".getBytes("ISO-8859-1"));
      s.getOutputStream().flush();
      BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "ISO-8859-1"));
      assertEquals("ok", in.readLine());
    }
    assertTrue(w.getPose().toPositionLine(false).startsWith("250 250 100"));
  }
}
