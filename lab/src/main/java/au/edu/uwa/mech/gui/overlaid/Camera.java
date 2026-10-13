package au.edu.uwa.mech.gui.overlaid;

import java.awt.Point;

import au.edu.uwa.mech.maths.geom.Line3D;
import au.edu.uwa.mech.maths.geom.Point3D;
import au.edu.uwa.mech.maths.geom.Vector3D;

/**
 * Pinhole camera described by a 3x4 calibration (DLT) matrix P: a world
 * point (x, y, z) maps to image (u, v) = (P0.X / P2.X, P1.X / P2.X) with
 * X = (x, y, z, 1). Displayed coordinates are image * scale + offset.
 */
public class Camera
{
  private final double[][] p = new double[3][4];
  private final double[][] mInv;     // inverse of the left 3x3 block of P
  private final Point3D centre;      // optical centre: P * (centre, 1) = 0
  private final Point offset;
  private final double scale;

  public Camera(double[][] calibration, Point offset, double scale)
  {
    for (int i = 0; i < 3; i++)
      System.arraycopy(calibration[i], 0, p[i], 0, 4);
    this.offset = new Point(offset);
    this.scale = scale;
    mInv = invert3(new double[][] {
        {p[0][0], p[0][1], p[0][2]}, {p[1][0], p[1][1], p[1][2]}, {p[2][0], p[2][1], p[2][2]}});
    double[] c = mul3(mInv, new double[] {-p[0][3], -p[1][3], -p[2][3]});
    centre = new Point3D(c[0], c[1], c[2]);
  }

  public Point3D getCentre() { return centre; }

  /** Project a world point to display coordinates. */
  public Point xyz2uv(Vector3D w)
  {
    double[] uv = project(w.x, w.y, w.z);
    return new Point((int) Math.round(uv[0] * scale + offset.x),
                     (int) Math.round(uv[1] * scale + offset.y));
  }

  /** Project a world point to sub-pixel image coordinates (no offset/scale). */
  public double[] project(double x, double y, double z)
  {
    double u = p[0][0] * x + p[0][1] * y + p[0][2] * z + p[0][3];
    double v = p[1][0] * x + p[1][1] * y + p[1][2] * z + p[1][3];
    double w = p[2][0] * x + p[2][1] * y + p[2][2] * z + p[2][3];
    return new double[] {u / w, v / w, w};
  }

  /** Project a 3D segment to a 2D display segment. */
  public Line projectLine(Line3D l)
  {
    return new Line(xyz2uv(l.getStart()), xyz2uv(l.getEnd()));
  }

  /** The ray through the optical centre that images to display point uv. */
  public Line3D uv2line(Point uv)
  {
    double u = (uv.x - offset.x) / scale, v = (uv.y - offset.y) / scale;
    double[] d = mul3(mInv, new double[] {u, v, 1});
    return new Line3D(centre, new Vector3D(d[0], d[1], d[2]));
  }

  /** Where the ray through uv meets the plane a*x + b*y + c*z + d = 0 (null if parallel). */
  public Point3D uv2xyzPlane(Point uv, double a, double b, double c, double d)
  {
    Line3D ray = uv2line(uv);
    Vector3D n = new Vector3D(a, b, c);
    double denom = n.dot(ray.getDirection());
    if (Math.abs(denom) < 1e-12)
      return null;
    double t = -(n.dot(ray.getStart()) + d) / denom;
    return ray.pointAt(t);
  }

  /** Where the ray through uv meets the work surface z = 0. */
  public Point3D uv2xyzSurface(Point uv)
  {
    return uv2xyzPlane(uv, 0, 0, 1, 0);
  }

  private static double[] mul3(double[][] m, double[] v)
  {
    double[] r = new double[3];
    for (int i = 0; i < 3; i++)
      r[i] = m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2];
    return r;
  }

  private static double[][] invert3(double[][] m)
  {
    double a = m[0][0], b = m[0][1], c = m[0][2];
    double d = m[1][0], e = m[1][1], f = m[1][2];
    double g = m[2][0], h = m[2][1], i = m[2][2];
    double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    if (det == 0)
      throw new IllegalArgumentException("calibration matrix is singular");
    return new double[][] {
        {(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det},
        {(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det},
        {(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det}};
  }
}
