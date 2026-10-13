package au.edu.uwa.mech.sim;

import java.awt.Color;

/** A wooden block on the table: centre of its base, size, rotation about z, colour. */
public final class Block
{
  public final String name;
  public final double sx, sy, sz;
  public final Color colour;
  // mutable state, guarded by the World's lock
  double x, y, z, yaw;

  public Block(String name, double x, double y, double z, double yawDegrees,
               double sx, double sy, double sz, Color colour)
  {
    this.name = name;
    this.x = x; this.y = y; this.z = z;
    this.yaw = Math.toRadians(yawDegrees);
    this.sx = sx; this.sy = sy; this.sz = sz;
    this.colour = colour;
  }

  /** Copy for handing to renderers. */
  Block copy()
  {
    Block b = new Block(name, x, y, z, 0, sx, sy, sz, colour);
    b.yaw = yaw;
    return b;
  }

  public double getX() { return x; }
  public double getY() { return y; }
  public double getZ() { return z; }
  public double getYaw() { return yaw; }
  public double top() { return z + sz; }

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
