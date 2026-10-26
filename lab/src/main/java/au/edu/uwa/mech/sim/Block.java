package au.edu.uwa.mech.sim;

import java.awt.Color;

/** A wooden piece on the table: its shape, centre of its base, size, rotation about z, colour. */
public final class Block
{
  public final String name;
  public final Shape shape;
  public final double sx, sy, sz;
  public final Color colour;
  // mutable state, guarded by the World's lock
  double x, y, z, yaw;

  /** A plain box: a cube when all sides match, else a bar. */
  public Block(String name, double x, double y, double z, double yawDegrees,
               double sx, double sy, double sz, Color colour)
  {
    this(name, sx == sy && sy == sz ? Shape.CUBE : Shape.BAR, x, y, z, yawDegrees, sx, sy, sz, colour);
  }

  public Block(String name, Shape shape, double x, double y, double z, double yawDegrees,
               double sx, double sy, double sz, Color colour)
  {
    this.name = name;
    this.shape = shape;
    this.x = x; this.y = y; this.z = z;
    this.yaw = Math.toRadians(yawDegrees);
    this.sx = sx; this.sy = sy; this.sz = sz;
    this.colour = colour;
  }

  /** Copy for handing to renderers. */
  Block copy()
  {
    Block b = new Block(name, shape, x, y, z, 0, sx, sy, sz, colour);
    b.yaw = yaw;
    return b;
  }

  public double getX() { return x; }
  public double getY() { return y; }
  public double getZ() { return z; }
  public double getYaw() { return yaw; }
  public double top() { return z + sz; }

  /** False for a roof: nothing can be stood on its slopes. */
  public boolean supports() { return shape != Shape.ROOF; }

  /** Points spread over this block's footprint, 1 mm in from its edges (so a neighbour only touching it is not under it). */
  double[][] footprintSamples()
  {
    int n = 5;
    double c = Math.cos(yaw), s = Math.sin(yaw);
    double[][] pts = new double[n * n][];
    int k = 0;
    for (int i = 0; i < n; i++)
      for (int j = 0; j < n; j++) {
        double lx = (sx / 2 - 1) * (2.0 * i / (n - 1) - 1), ly = (sy / 2 - 1) * (2.0 * j / (n - 1) - 1);
        pts[k++] = new double[] {x + c * lx - s * ly, y + s * lx + c * ly};
      }
    return pts;
  }

  /** True if the footprints of this block and o, each grown by margin, overlap (separating axes). */
  boolean footprintOverlaps(Block o, double margin)
  {
    for (double axis : new double[] {yaw, yaw + Math.PI / 2, o.yaw, o.yaw + Math.PI / 2}) {
      double ax = Math.cos(axis), ay = Math.sin(axis);
      double gap = Math.abs((o.x - x) * ax + (o.y - y) * ay);
      if (gap >= halfExtent(axis) + o.halfExtent(axis) + 2 * margin)
        return false;
    }
    return true;
  }

  /** Half the footprint's width measured along a horizontal direction. */
  private double halfExtent(double angle)
  {
    return widthAlong(angle) / 2;
  }

  /** True if the point (px, py) lies within this block's footprint, grown by margin. */
  boolean footprintContains(double px, double py, double margin)
  {
    double dx = px - x, dy = py - y;
    double c = Math.cos(-yaw), s = Math.sin(-yaw);
    double lx = c * dx - s * dy, ly = s * dx + c * dy;
    return Math.abs(lx) <= sx / 2 + margin && Math.abs(ly) <= sy / 2 + margin;
  }

  /** Width of the block measured along a horizontal direction (for closing the fingers on it). */
  double widthAlong(double angle)
  {
    double a = angle - yaw;
    return Math.abs(Math.cos(a)) * sx + Math.abs(Math.sin(a)) * sy;
  }
}
