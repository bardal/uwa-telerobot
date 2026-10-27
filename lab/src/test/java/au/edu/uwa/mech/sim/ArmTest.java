package au.edu.uwa.mech.sim;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The arm is drawn to the ABB IRB 1400's dimensions (product specification 3HAC 9376-1, figure 3). */
public class ArmTest
{
  private static double dist(double[] a, double[] b)
  {
    return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
  }

  private static void assertPoint(String what, double[] want, double[] got)
  {
    assertEquals(what + " distance", 0, dist(want, got), 1e-6);
  }

  @Test
  public void jointsAreAtTheIrb1400Dimensions()
  {
    Scene.Joints j = Scene.joints(new RobotPose(250, 250, 100, -90, 0, true));
    // axis 2 is 475 mm up and 150 mm out from axis 1, towards the wrist
    assertEquals(475, j.shoulder[2] - Scene.BASE[2], 1e-9);
    assertEquals(150, Math.hypot(j.shoulder[0] - Scene.BASE[0], j.shoulder[1] - Scene.BASE[1]), 1e-9);
    // lower arm 600; the forearm axis runs 120 above axis 3, 720 to the wrist centre
    assertEquals(600, dist(j.shoulder, j.elbow), 1e-6);
    assertEquals(Math.hypot(720, 120), dist(j.elbow, j.wristReached), 1e-6);
    double[] onAxis = {j.elbow[0] + j.normal[0] * 120, j.elbow[1] + j.normal[1] * 120, j.elbow[2] + j.normal[2] * 120};
    double[] alongAxis = {onAxis[0] + j.forearm[0] * 720, onAxis[1] + j.forearm[1] * 720, onAxis[2] + j.forearm[2] * 720};
    assertPoint("wrist centre on the forearm axis", j.wristReached, alongAxis);
    // the wrist centre is the flange's 85 mm plus the gripper's 88 mm above the tool point
    assertPoint("wrist centre", new double[] {250, 250, 100 + 85 + 88}, j.wrist);
  }

  @Test
  public void reachesTheWholeTableWithinTheArmLimits()
  {
    for (double z : new double[] {0, 300})
      for (double[] xy : new double[][] {{-150, -150}, {650, -150}, {-150, 650}, {650, 650}, {250, -150}, {250, 250}}) {
        Scene.Joints j = Scene.joints(new RobotPose(xy[0], xy[1], z, -90, 0, true));
        assertPoint("wrist reached at " + xy[0] + "," + xy[1] + "," + z, j.wrist, j.wristReached);
      }
  }
}
