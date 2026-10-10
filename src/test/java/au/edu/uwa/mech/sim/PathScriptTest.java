package au.edu.uwa.mech.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class PathScriptTest
{
  static final RobotPose HOME = new RobotPose(250, 250, 100, -90, 0, true);

  @Test
  public void waypointsAreSeparatedBySemicolons()
  {
    // from the February 2000 command log
    List<RobotPose> p = PathScript.parse("z20;y180,x122,s0", HOME);
    assertEquals(2, p.size());
    assertEquals(new RobotPose(250, 250, 20, -90, 0, true), p.get(0));
    assertEquals(new RobotPose(122, 180, 20, 0, 0, true), p.get(1));
  }

  @Test
  public void gripperAxis()
  {
    List<RobotPose> p = PathScript.parse("x145,y160;z5;g1", HOME);
    assertEquals(3, p.size());
    assertTrue(p.get(1).open);
    assertFalse(p.get(2).open);
    assertTrue(PathScript.parse("gopen", p.get(2)).get(0).open);
    assertFalse(PathScript.parse("c", HOME).get(0).open);
  }

  @Test
  public void relativeValuesApplyToOneAxis()
  {
    RobotPose p = PathScript.parse("rx10,rz-20,y5", HOME).get(0);
    assertEquals(260, p.x, 0);
    assertEquals(80, p.z, 0);
    assertEquals(5, p.y, 0);
  }

  @Test
  public void rockingChairTilt()
  {
    assertEquals(-45, PathScript.parse("tf", HOME).get(0).tilt, 0);
    assertEquals(45, PathScript.parse("tb", HOME).get(0).tilt, 0);
    assertEquals(30, PathScript.parse("t30", HOME).get(0).tilt, 0);
  }

  @Test
  public void trailingSeparatorAddsNothing()
  {
    assertEquals(1, PathScript.parse("z25;", HOME).size());
  }

  @Test
  public void workspaceLimitsRejectBelowTheFloor()
  {
    assertTrue(VirtualRobotServer.check(HOME.with('z', 0)) != null);
    assertEquals(null, VirtualRobotServer.check(HOME));
  }

  @Test
  public void positionLineMatchesRobotServerFormat()
  {
    assertEquals("250 250 100 1 -90 0 1 0 0 Stopped", HOME.toPositionLine(false));
  }
}
