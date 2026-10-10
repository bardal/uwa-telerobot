package au.edu.uwa.mech.maths.geom;

/** A fixed-length vector of doubles; used for homogeneous coordinates. */
public class VectorN implements java.io.Serializable
{
  protected final double[] v;

  public VectorN(int n) { v = new double[n]; }

  public VectorN(double[] values) { v = values.clone(); }

  /** The homogeneous 4-vector (p.x, p.y, p.z, w). */
  public VectorN(Vector3D p, double w)
  {
    this(new double[] {p.x, p.y, p.z, w});
  }

  public int size() { return v.length; }

  public double elementAt(int i) { return v[i]; }

  public void setElementAt(double value, int i) { v[i] = value; }

  static final long serialVersionUID = 1L;
}
