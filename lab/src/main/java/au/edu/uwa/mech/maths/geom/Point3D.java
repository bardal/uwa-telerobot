package au.edu.uwa.mech.maths.geom;

/** A position in 3-space; the difference of two points is a {@link Vector3D}. */
public class Point3D extends Vector3D
{
  public Point3D() {}

  public Point3D(double x, double y, double z) { super(x, y, z); }

  public Point3D(Vector3D v) { super(v); }

  /** The point displaced by v. */
  public Point3D add(Vector3D v) { return new Point3D(x + v.x, y + v.y, z + v.z); }

  public double distance(Point3D p) { return sub(p).length(); }

  static final long serialVersionUID = 1L;
}
