package au.edu.uwa.mech.maths.geom;

/** A 4x4 homogeneous transform. Starts as the identity; the make* methods overwrite it. */
public class Matrix4x4 implements java.io.Serializable
{
  protected final double[][] m = new double[4][4];

  public Matrix4x4() { makeIdentity(); }

  public void makeIdentity()
  {
    for (int i = 0; i < 4; i++)
      for (int j = 0; j < 4; j++)
        m[i][j] = i == j ? 1 : 0;
  }

  public void makeTrans(Vector3D t)
  {
    makeIdentity();
    m[0][3] = t.x;
    m[1][3] = t.y;
    m[2][3] = t.z;
  }

  /** Rotation by angle radians about the x axis. */
  public void makeRotX(double angle)
  {
    makeIdentity();
    double c = Math.cos(angle), s = Math.sin(angle);
    m[1][1] = c; m[1][2] = -s;
    m[2][1] = s; m[2][2] = c;
  }

  /** Rotation by angle radians about the y axis. */
  public void makeRotY(double angle)
  {
    makeIdentity();
    double c = Math.cos(angle), s = Math.sin(angle);
    m[0][0] = c;  m[0][2] = s;
    m[2][0] = -s; m[2][2] = c;
  }

  /** Rotation by angle radians about the z axis. */
  public void makeRotZ(double angle)
  {
    makeIdentity();
    double c = Math.cos(angle), s = Math.sin(angle);
    m[0][0] = c; m[0][1] = -s;
    m[1][0] = s; m[1][1] = c;
  }

  public double get(int row, int col) { return m[row][col]; }

  public void set(int row, int col, double value) { m[row][col] = value; }

  /** this * other */
  public Matrix4x4 mult(Matrix4x4 other)
  {
    Matrix4x4 r = new Matrix4x4();
    for (int i = 0; i < 4; i++)
      for (int j = 0; j < 4; j++) {
        double s = 0;
        for (int k = 0; k < 4; k++)
          s += m[i][k] * other.m[k][j];
        r.m[i][j] = s;
      }
    return r;
  }

  /** this * v, for a 4-vector v */
  public VectorN mult(VectorN v)
  {
    VectorN r = new VectorN(4);
    for (int i = 0; i < 4; i++) {
      double s = 0;
      for (int k = 0; k < 4; k++)
        s += m[i][k] * v.elementAt(k);
      r.setElementAt(s, i);
    }
    return r;
  }

  /** Transform a point (w = 1), dividing through by the resulting w. */
  public Point3D transform(Vector3D p)
  {
    VectorN h = mult(new VectorN(p, 1));
    double w = h.elementAt(3);
    return new Point3D(h.elementAt(0) / w, h.elementAt(1) / w, h.elementAt(2) / w);
  }

  static final long serialVersionUID = 1L;
}
