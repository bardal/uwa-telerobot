package au.edu.uwa.mech.gui.overlaid;

import java.awt.Graphics;
import java.awt.Point;

import au.edu.uwa.mech.maths.Point3d;
import au.edu.uwa.mech.maths.geom.Point3D;

/** A horizontal "needle" from the gripper position pointing along the spin angle. */
public class SpinCursor extends OverlaidComponent
{
  /** Needle length, in mm. */
  public static double LENGTH = 50;

  protected Line needle;

  protected boolean project()
  {
    if (cam == null || currentPose == null)
      return false;
    Point3d p = currentPose.position;
    double s = currentPose.orientation.spin();
    needle = new Line(cam.xyz2uv(p),
                      cam.xyz2uv(new Point3D(p.x + LENGTH * Math.cos(s), p.y + LENGTH * Math.sin(s), p.z)));
    return true;
  }

  public void draw(Graphics g)
  {
    if (!visible || !project())
      return;
    needle.draw(g, currentColor());
    Point tip = needle.getEnd();
    g.fillRect(tip.x - 2, tip.y - 2, 5, 5);
  }

  /** Grabbing is by the outer part of the needle, so the peak marker stays reachable. */
  public boolean contains(Point m)
  {
    return visible && project() && needle.contains(m) && needle.proportion(m) > 0.3;
  }
}
