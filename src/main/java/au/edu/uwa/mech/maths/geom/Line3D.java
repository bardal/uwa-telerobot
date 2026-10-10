package au.edu.uwa.mech.maths.geom;

/**
 * A line in 3-space, held as a start point and a direction. When built from
 * two points it is also a segment, with the second point at parameter 1.
 */
public class Line3D implements java.io.Serializable
{
  protected Point3D start;
  protected Vector3D direction;

  /** The segment from a to b. */
  public Line3D(Point3D a, Point3D b)
  {
    start = new Point3D(a);
    direction = b.sub(a);
  }

  /** The line through p with the given direction. */
  public Line3D(Point3D p, Vector3D direction)
  {
    start = new Point3D(p);
    this.direction = new Vector3D(direction);
  }

  public Point3D getStart() { return start; }

  public Point3D getEnd() { return pointAt(1); }

  public Vector3D getDirection() { return direction; }

  public Point3D pointAt(double t) { return start.add(direction.scale(t)); }

  /** Length of the segment (start to end). */
  public double length() { return direction.length(); }

  /**
   * The midpoint of the shortest segment joining this line and another,
   * which is the usual estimate of where two nearly-intersecting rays meet.
   * For parallel lines, the midpoint between this start and its foot on the other.
   */
  public Point3D midPoint(Line3D other)
  {
    double[] st = closestParameters(other);
    Point3D p = pointAt(st[0]), q = other.pointAt(st[1]);
    return new Point3D((p.x + q.x) / 2, (p.y + q.y) / 2, (p.z + q.z) / 2);
  }

  /** The point on this (infinite) line closest to p. */
  public Point3D closestPointOnLineTo(Point3D p)
  {
    double a = direction.dot(direction);
    return a == 0 ? new Point3D(start) : pointAt(p.sub(start).dot(direction) / a);
  }

  /** The point on this line closest to the other line. */
  public Point3D closestPoint(Line3D other)
  {
    return pointAt(closestParameters(other)[0]);
  }

  /** Parameters (s on this, t on other) of the closest pair of points. */
  protected double[] closestParameters(Line3D other)
  {
    Vector3D w = start.sub(other.start);
    double a = direction.dot(direction);
    double b = direction.dot(other.direction);
    double c = other.direction.dot(other.direction);
    double d = direction.dot(w);
    double e = other.direction.dot(w);
    double denom = a * c - b * b;
    if (Math.abs(denom) < 1e-12 * a * c)  // parallel
      return new double[] {0, e / c};
    return new double[] {(b * e - c * d) / denom, (a * e - b * d) / denom};
  }

  public String toString() { return "[line " + start + " -> " + getEnd() + "]"; }

  static final long serialVersionUID = 1L;
}
