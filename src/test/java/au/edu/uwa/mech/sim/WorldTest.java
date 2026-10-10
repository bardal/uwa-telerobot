package au.edu.uwa.mech.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    // the help page's example: red block onto the blue one
    String reply = robot.execute("gbh move x120,y150,z60;z15;g1;z80;x330,y120;z45;g0;z90");
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
}
