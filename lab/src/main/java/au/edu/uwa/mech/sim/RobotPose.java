package au.edu.uwa.mech.sim;

/**
 * A gripper pose in RobotServer ("GBH") units: position in mm, spin and
 * tilt in degrees, gripper open or closed. Immutable.
 */
public final class RobotPose
{
  public final double x, y, z, spin, tilt;
  public final boolean open;

  public RobotPose(double x, double y, double z, double spin, double tilt, boolean open)
  {
    this.x = x; this.y = y; this.z = z;
    this.spin = spin; this.tilt = tilt;
    this.open = open;
  }

  public RobotPose with(char axis, double v)
  {
    switch (axis) {
      case 'x': return new RobotPose(v, y, z, spin, tilt, open);
      case 'y': return new RobotPose(x, v, z, spin, tilt, open);
      case 'z': return new RobotPose(x, y, v, spin, tilt, open);
      case 's': return new RobotPose(x, y, z, v, tilt, open);
      case 't': return new RobotPose(x, y, z, spin, v, open);
      default: throw new IllegalArgumentException("axis " + axis);
    }
  }

  public RobotPose withGripper(boolean open)
  {
    return new RobotPose(x, y, z, spin, tilt, open);
  }

  public double get(char axis)
  {
    switch (axis) {
      case 'x': return x;
      case 'y': return y;
      case 'z': return z;
      case 's': return spin;
      case 't': return tilt;
      default: throw new IllegalArgumentException("axis " + axis);
    }
  }

  /** Spin as an angle from the +x axis in radians (GBH spin -90 points along +x). */
  public double spinRadians() { return Math.toRadians(spin + 90); }

  /** Lean of the tool axis from vertical, in radians, towards the spin direction. */
  public double tiltRadians() { return Math.toRadians(-tilt); }

  /** The line RobotServer streams on its position port (RobotPose operator<< + state word). */
  public String toPositionLine(boolean moving)
  {
    return fmt(x) + " " + fmt(y) + " " + fmt(z) + " 1 " + fmt(spin) + " " + fmt(tilt) + " "
        + (open ? 1 : 0) + " 0 0 " + (moving ? "Moving" : "Stopped");
  }

  private static String fmt(double d)
  {
    if (Math.abs(d - Math.rint(d)) < 1e-6)
      return Long.toString(Math.round(d));
    return String.format(java.util.Locale.ROOT, "%.3f", d);
  }

  public boolean equals(Object o)
  {
    if (!(o instanceof RobotPose))
      return false;
    RobotPose p = (RobotPose) o;
    return x == p.x && y == p.y && z == p.z && spin == p.spin && tilt == p.tilt && open == p.open;
  }

  public int hashCode() { return java.util.Objects.hash(x, y, z, spin, tilt, open); }

  public String toString() { return toPositionLine(false); }
}
