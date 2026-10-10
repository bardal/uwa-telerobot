package au.edu.uwa.mech.sim;

import Jama.Matrix;

/**
 * A pinhole camera placed in the workcell: eye position, a point it looks
 * at, horizontal field of view and image size. It produces both images
 * (through {@link Renderer}) and the 3x4 calibration matrix that the
 * client overlays use, so the two agree exactly.
 */
public class VirtualCamera
{
  public final String name;
  final double[] eye, target;
  final double fovDegrees;
  private int width, height;
  // derived
  private double f, cx, cy;
  private final double[][] r = new double[3][3]; // rows: right, down, forward

  public VirtualCamera(String name, double[] eye, double[] target, double fovDegrees, int width, int height)
  {
    this.name = name;
    this.eye = eye.clone();
    this.target = target.clone();
    this.fovDegrees = fovDegrees;
    double[] fwd = norm(sub(target, eye));
    double[] right = norm(cross(fwd, new double[] {0, 0, 1}));
    double[] down = cross(fwd, right);
    r[0] = right;
    r[1] = down;
    r[2] = fwd;
    setSize(width, height);
  }

  public synchronized void setSize(int width, int height)
  {
    this.width = width;
    this.height = height;
    f = width / 2.0 / Math.tan(Math.toRadians(fovDegrees) / 2);
    cx = width / 2.0;
    cy = height / 2.0;
  }

  public synchronized int getWidth() { return width; }

  public synchronized int getHeight() { return height; }

  /** Camera-frame coordinates (right, down, forward) of a world point. */
  double[] toCamera(double x, double y, double z)
  {
    double dx = x - eye[0], dy = y - eye[1], dz = z - eye[2];
    return new double[] {
        r[0][0] * dx + r[0][1] * dy + r[0][2] * dz,
        r[1][0] * dx + r[1][1] * dy + r[1][2] * dz,
        r[2][0] * dx + r[2][1] * dy + r[2][2] * dz};
  }

  /** World direction of the ray through image point (u, v), for an image scale factor. */
  double[] rayDirection(double u, double v, double scale)
  {
    double xc = (u / scale - cx) / f, yc = (v / scale - cy) / f;
    return new double[] {
        r[0][0] * xc + r[1][0] * yc + r[2][0],
        r[0][1] * xc + r[1][1] * yc + r[2][1],
        r[0][2] * xc + r[1][2] * yc + r[2][2]};
  }

  double focal() { return f; }
  double centreU() { return cx; }
  double centreV() { return cy; }

  /** The DLT calibration matrix P = K [R | -R eye], scaled so P[2][3] = 1. */
  public synchronized Matrix calibrationMatrix()
  {
    double[][] k = {{f, 0, cx}, {0, f, cy}, {0, 0, 1}};
    double[] t = new double[3];
    for (int i = 0; i < 3; i++)
      t[i] = -(r[i][0] * eye[0] + r[i][1] * eye[1] + r[i][2] * eye[2]);
    double[][] p = new double[3][4];
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++)
        p[i][j] = k[i][0] * r[0][j] + k[i][1] * r[1][j] + k[i][2] * r[2][j];
      p[i][3] = k[i][0] * t[0] + k[i][1] * t[1] + k[i][2] * t[2];
    }
    double s = p[2][3];
    for (int i = 0; i < 3; i++)
      for (int j = 0; j < 4; j++)
        p[i][j] /= s;
    return new Matrix(p);
  }

  static double[] sub(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

  static double[] cross(double[] a, double[] b)
  {
    return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
  }

  static double[] norm(double[] a)
  {
    double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    return new double[] {a[0] / l, a[1] / l, a[2] / l};
  }
}
