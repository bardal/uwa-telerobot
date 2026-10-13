package au.edu.uwa.mech.maths.geom;

/** A 3-vector of doubles with the handful of operations the telerobot code needs. */
public class Vector3D implements java.io.Serializable
{
  public double x, y, z;

  public Vector3D() {}

  public Vector3D(double x, double y, double z)
  {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public Vector3D(Vector3D v)
  {
    this(v.x, v.y, v.z);
  }

  public Vector3D add(Vector3D v) { return new Vector3D(x + v.x, y + v.y, z + v.z); }

  public Vector3D sub(Vector3D v) { return new Vector3D(x - v.x, y - v.y, z - v.z); }

  public Vector3D scale(double s) { return new Vector3D(x * s, y * s, z * s); }

  public double dot(Vector3D v) { return x * v.x + y * v.y + z * v.z; }

  public Vector3D cross(Vector3D v)
  {
    return new Vector3D(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
  }

  public double length() { return Math.sqrt(dot(this)); }

  public Vector3D normalised()
  {
    double l = length();
    return l == 0 ? new Vector3D(this) : scale(1 / l);
  }

  public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }

  static final long serialVersionUID = 1L;
}
