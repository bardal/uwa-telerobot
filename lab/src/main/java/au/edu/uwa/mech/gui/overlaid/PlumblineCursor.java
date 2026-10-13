package au.edu.uwa.mech.gui.overlaid;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;

import au.edu.uwa.mech.maths.Point3d;
import au.edu.uwa.mech.maths.geom.Point3D;

/**
 * The positional part of the stick cursor: a "peak" marker at the gripper
 * position, a plumb line dropped to a "base" marker on the work surface,
 * and two base lines through the base parallel to the x and y axes.
 */
public class PlumblineCursor extends OverlaidComponent
{
  public enum Part { NONE, PEAK, BASE, HEIGHT, X_LINE, Y_LINE }

  /** Half-length of the base lines, in mm. */
  public static double BASELINE_HALF_LENGTH = 60;
  static final int MARKER_RADIUS = 5;

  protected boolean baselinesVisible = true;
  protected Part focusPart = Part.NONE;

  // projected geometry, refreshed on each draw / hit test
  protected Point peak, base;
  protected Line height, xLine, yLine;

  public Point3d getLocation() { return currentPose.position; }

  public void setBaselinesVisible(boolean v) { baselinesVisible = v; }

  public void setFocusPart(Part p)
  {
    focusPart = p;
    focus = p != Part.NONE;
  }

  public void setFocus(boolean f)
  {
    super.setFocus(f);
    if (!f)
      focusPart = Part.NONE;
  }

  protected boolean project()
  {
    if (cam == null || currentPose == null)
      return false;
    Point3d p = currentPose.position;
    double l = BASELINE_HALF_LENGTH;
    peak = cam.xyz2uv(p);
    base = cam.xyz2uv(new Point3D(p.x, p.y, 0));
    height = new Line(base, peak);
    xLine = new Line(cam.xyz2uv(new Point3D(p.x - l, p.y, 0)), cam.xyz2uv(new Point3D(p.x + l, p.y, 0)));
    yLine = new Line(cam.xyz2uv(new Point3D(p.x, p.y - l, 0)), cam.xyz2uv(new Point3D(p.x, p.y + l, 0)));
    return true;
  }

  public void draw(Graphics g)
  {
    if (!visible || !project())
      return;
    if (baselinesVisible) {
      xLine.draw(g, colourFor(Part.X_LINE));
      yLine.draw(g, colourFor(Part.Y_LINE));
    }
    height.draw(g, colourFor(Part.HEIGHT));
    marker(g, base, colourFor(Part.BASE));
    marker(g, peak, colourFor(Part.PEAK));
  }

  private Color colourFor(Part p) { return focusPart == p ? colorFocus : colorNoFocus; }

  private static void marker(Graphics g, Point c, Color col)
  {
    g.setColor(col);
    g.drawOval(c.x - MARKER_RADIUS, c.y - MARKER_RADIUS, 2 * MARKER_RADIUS, 2 * MARKER_RADIUS);
  }

  /** Which part, if any, display point m is on. Markers win over lines. */
  public Part partAt(Point m)
  {
    if (!visible || !project())
      return Part.NONE;
    if (m.distance(peak) <= MARKER_RADIUS + 2)
      return Part.PEAK;
    if (m.distance(base) <= MARKER_RADIUS + 2)
      return Part.BASE;
    if (baselinesVisible && xLine.contains(m))
      return Part.X_LINE;
    if (baselinesVisible && yLine.contains(m))
      return Part.Y_LINE;
    if (height.contains(m))
      return Part.HEIGHT;
    return Part.NONE;
  }

  /** Fraction of the way up the plumb line (0 = base, 1 = peak) nearest m. */
  public double heightFraction(Point m)
  {
    return project() ? height.proportion(m) : 0;
  }

  public boolean contains(Point m) { return partAt(m) != Part.NONE; }
}
