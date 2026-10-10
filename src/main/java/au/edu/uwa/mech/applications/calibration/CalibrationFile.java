package au.edu.uwa.mech.applications.calibration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import Jama.Matrix;

/**
 * Camera calibration: the 3x4 DLT matrix P (normalised so P[2][3] = 1)
 * relating world points to image points, plus the text file format used
 * for it (a "Calibration Data" line followed by three rows of four numbers).
 */
public final class CalibrationFile
{
  private CalibrationFile() {}

  /** Least-squares DLT from at least six non-coplanar world/image correspondences. */
  public static Matrix solve(List<double[]> xyz, List<double[]> uv)
  {
    int n = Math.min(xyz.size(), uv.size());
    if (n < 6)
      throw new IllegalArgumentException("need at least 6 points, got " + n);
    Matrix a = new Matrix(2 * n, 11);
    Matrix b = new Matrix(2 * n, 1);
    for (int i = 0; i < n; i++) {
      double x = xyz.get(i)[0], y = xyz.get(i)[1], z = xyz.get(i)[2];
      double u = uv.get(i)[0], v = uv.get(i)[1];
      double[] ru = {x, y, z, 1, 0, 0, 0, 0, -u * x, -u * y, -u * z};
      double[] rv = {0, 0, 0, 0, x, y, z, 1, -v * x, -v * y, -v * z};
      for (int j = 0; j < 11; j++) {
        a.set(2 * i, j, ru[j]);
        a.set(2 * i + 1, j, rv[j]);
      }
      b.set(2 * i, 0, u);
      b.set(2 * i + 1, 0, v);
    }
    Matrix p = a.solve(b); // QR least squares
    Matrix cal = new Matrix(3, 4);
    for (int k = 0; k < 11; k++)
      cal.set(k / 4, k % 4, p.get(k, 0));
    cal.set(2, 3, 1);
    return cal;
  }

  /** RMS reprojection error, in pixels. */
  public static double rmsError(Matrix cal, List<double[]> xyz, List<double[]> uv)
  {
    double sum = 0;
    int n = Math.min(xyz.size(), uv.size());
    for (int i = 0; i < n; i++) {
      double[] w = xyz.get(i);
      double[] h = new double[3];
      for (int r = 0; r < 3; r++)
        h[r] = cal.get(r, 0) * w[0] + cal.get(r, 1) * w[1] + cal.get(r, 2) * w[2] + cal.get(r, 3);
      double du = h[0] / h[2] - uv.get(i)[0], dv = h[1] / h[2] - uv.get(i)[1];
      sum += du * du + dv * dv;
    }
    return Math.sqrt(sum / n);
  }

  /**
   * Read "label index a b c ..." lines (as in xyz.txt and uv.txt), keeping
   * the given number of trailing values from each.
   */
  public static List<double[]> readPoints(String file, int values) throws IOException
  {
    List<double[]> pts = new ArrayList<double[]>();
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = in.readLine()) != null) {
        String[] w = line.trim().split("\\s+");
        if (w.length < values + 2)
          continue;
        double[] p = new double[values];
        for (int i = 0; i < values; i++)
          p[i] = Double.parseDouble(w[2 + i]);
        pts.add(p);
      }
    }
    return pts;
  }

  public static void write(Matrix cal, String file, String comment) throws IOException
  {
    try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
      w.println("* " + comment);
      w.println();
      w.println("Calibration Data");
      for (int r = 0; r < 3; r++)
        w.println(String.format(Locale.ROOT, "  %16.9f %16.9f %16.9f %16.9f",
                                cal.get(r, 0), cal.get(r, 1), cal.get(r, 2), cal.get(r, 3)));
    }
  }

  /** Read the matrix following the line "Calibration Data". */
  public static Matrix read(String file) throws IOException
  {
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = in.readLine()) != null) {
        if (!line.trim().equals("Calibration Data"))
          continue;
        Matrix cal = new Matrix(3, 4);
        for (int r = 0; r < 3; r++) {
          String[] w = in.readLine().trim().split("\\s+");
          for (int c = 0; c < 4; c++)
            cal.set(r, c, Double.parseDouble(w[c]));
        }
        return cal;
      }
    }
    throw new IOException("no 'Calibration Data' block in " + file);
  }
}
