package au.edu.uwa.mech.devices.robot;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import au.edu.uwa.mech.maths.Point3d;
import au.edu.uwa.mech.maths.Pose3d;

/**
 * Writes webui/test/fixtures/pathscript.json, the reference strings for the TypeScript
 * port of MMoveCommand.poseAsGBHString, pointAsGBHString and stateAsGBHString. Each pose is
 * given as x, y, z in mm and spin, tilt in degrees, set through Orientation3d.spinDeg/tiltDeg
 * as the applet's cursor and RobotState.random() do. Run by the normal test suite.
 */
public class PathscriptFixtures
{
  /** x, y, z, spin (deg), tilt (deg); includes halves, negatives and the home pose. */
  static final double[][] POSES = {
      {149.6, 349.2, 39.4, -90, 0},
      {250, 250, 100, -90, 0},
      {120, 150, 60, -90, 0},
      {330.5, 120.5, 90.5, 44.5, -44.5},
      {-2.5, -0.5, 0.49999, -45.5, 45.5},
      {499.99, 0.01, 250.5, 12.345, -7.89},
      {0, 0, 0, 0, 0},
      {1e-9, -1e-9, 15, -89.5, 0.5},
      {372.123456, 18.987654, 77.7, 30.000001, -29.999999},
      {200.4999999, 360.5000001, 15.5, -0.5, -0.4999},
  };

  @Test
  public void writePathscriptFixtures() throws Exception
  {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"poses\": [\n");
    for (int i = 0; i < POSES.length; i++) {
      double[] p = POSES[i];
      Pose3d pose = new Pose3d();
      pose.position.x = p[0];
      pose.position.y = p[1];
      pose.position.z = p[2];
      pose.orientation.spinDeg(p[3]);
      pose.orientation.tiltDeg(p[4]);
      Point3d point = new Point3d(p[0], p[1], p[2]);
      RobotState open = new RobotState(new Pose3d(pose), Gripper.newOpen());
      RobotState closed = new RobotState(new Pose3d(pose), Gripper.newClosed());

      sb.append("    {\"in\": [");
      for (int j = 0; j < p.length; j++)
        sb.append(j > 0 ? ", " : "").append(Double.toString(p[j]));
      sb.append("], \"pose\": ").append(quote(MMoveCommand.poseAsGBHString(pose)));
      sb.append(", \"point\": ").append(quote(MMoveCommand.pointAsGBHString(point)));
      sb.append(", \"stateOpen\": ").append(quote(MMoveCommand.stateAsGBHString(open)));
      sb.append(", \"stateClosed\": ").append(quote(MMoveCommand.stateAsGBHString(closed)));
      sb.append(i < POSES.length - 1 ? "},\n" : "}\n");
    }
    sb.append("  ]\n}\n");

    Pose3d first = new Pose3d();
    first.position.x = 149.6;
    first.position.y = 349.2;
    first.position.z = 39.4;
    first.orientation.spinDeg(-90);
    first.orientation.tiltDeg(0);
    assertEquals("x150,y349,s-90,t0,z39", MMoveCommand.poseAsGBHString(first));

    File out = new File(new File(System.getProperty("user.dir")).getParentFile(), "webui/test/fixtures/pathscript.json");
    out.getParentFile().mkdirs();
    Files.write(out.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static String quote(String s)
  {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
