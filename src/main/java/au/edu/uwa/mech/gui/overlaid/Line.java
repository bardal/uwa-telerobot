package au.edu.uwa.mech.gui.overlaid;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;

/** A 2D segment in display coordinates, with hit testing for dragging. */
public class Line
{
  protected final Point start, end;

  public Line(Point start, Point end)
  {
    this.start = new Point(start);
    this.end = new Point(end);
  }

  public Point getStart() { return start; }

  public Point getEnd() { return end; }

  public void draw(Graphics g, Color c)
  {
    g.setColor(c);
    g.drawLine(start.x, start.y, end.x, end.y);
  }

  /** Distance from p to the nearest point of the segment. */
  public double distance(Point p)
  {
    double dx = end.x - start.x, dy = end.y - start.y;
    double len2 = dx * dx + dy * dy;
    double t = len2 == 0 ? 0 : ((p.x - start.x) * dx + (p.y - start.y) * dy) / len2;
    t = Math.max(0, Math.min(1, t));
    double cx = start.x + t * dx - p.x, cy = start.y + t * dy - p.y;
    return Math.sqrt(cx * cx + cy * cy);
  }

  /** Where along the segment the point nearest p lies: 0 at start, 1 at end. */
  public double proportion(Point p)
  {
    double dx = end.x - start.x, dy = end.y - start.y;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0)
      return 0;
    return Math.max(0, Math.min(1, ((p.x - start.x) * dx + (p.y - start.y) * dy) / len2));
  }

  public boolean contains(Point p) { return distance(p) <= TOLERANCE; }

  public static final int TOLERANCE = 4;
}
