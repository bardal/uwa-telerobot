package au.edu.uwa.mech.gui.overlaid;

import java.awt.Graphics;
import java.awt.Point;

import au.edu.uwa.mech.maths.Point3d;
import au.edu.uwa.mech.maths.geom.Point3D;

/**
 * A line from the gripper position along the tool axis: vertical at zero
 * tilt, leaning towards the spin direction as tilt increases.
 */
public class TiltCursor extends OverlaidComponent
{
  /** Length, in mm. */
  public static double LENGTH = 50;

  protected Line tilter;

  public double getSpin() { return currentPose.orientation.spin(); }

  protected boolean project()
  {
    if (cam == null || currentPose == null)
      return false;
    Point3d p = currentPose.position;
    double s = getSpin(), t = currentPose.orientation.tilt();
    double h = LENGTH * Math.sin(t);
    tilter = new Line(cam.xyz2uv(p), cam.xyz2uv(new Point3D(
        p.x + h * Math.cos(s), p.y + h * Math.sin(s), p.z + LENGTH * Math.cos(t))));
    return true;
  }

  public void draw(Graphics g)
  {
    if (!visible || !project())
      return;
    tilter.draw(g, currentColor());
    Point tip = tilter.getEnd();
    g.drawOval(tip.x - 3, tip.y - 3, 6, 6);
  }

  public boolean contains(Point m)
  {
    return visible && project() && tilter.contains(m) && tilter.proportion(m) > 0.3;
  }
}
