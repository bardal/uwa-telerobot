package au.edu.uwa.mech.sim;

import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.maths.geom.Line3D;
import au.edu.uwa.mech.maths.geom.Point3D;

/**
 * Writes webui/test/fixtures/camera.json, the reference values for the TypeScript
 * port of gui.overlaid.Camera. Run by the normal test suite; the output is
 * deterministic (sorted keys, 6 decimals), so a clean checkout reproduces it byte for byte.
 */
public class GeometryFixtures
{
  /** Fixed points: table corners, block centres, the arm base, the home pose and the table centre. */
  static final double[][] POINTS = {
      {-220, -170, 0}, {720, -170, 0}, {720, 720, 0}, {-220, 720, 0},
      {120, 150, 15}, {330, 120, 15}, {200, 360, 15}, {400, 330, 15}, {260, 200, 15},
      {Scene.BASE[0], Scene.BASE[1], Scene.BASE[2]}, {250, 250, 100}, {250, 250, 0}};
  static final int[][] PIXELS = {{50, 40}, {200, 150}, {350, 260}, {100, 250}, {300, 60}, {200, 220}};
  static final double DISPLAY_SCALE = 1.75;
  static final Point DISPLAY_OFFSET = new Point(12, 7);

  @Test
  public void writeCameraFixtures() throws Exception
  {
    List<Object> cameras = new ArrayList<Object>();
    for (VirtualCamera vc : VirtualLab.defaultCameras())
      cameras.add(describe(vc));
    Map<String, Object> root = new TreeMap<String, Object>();
    root.put("cameras", cameras);

    File out = new File(new File(System.getProperty("user.dir")).getParentFile(), "webui/test/fixtures/camera.json");
    out.getParentFile().mkdirs();
    StringBuilder sb = new StringBuilder();
    json(root, sb, 0);
    sb.append('\n');
    Files.write(out.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    assertTrue(out.length() > 1000);
  }

  private static Map<String, Object> describe(VirtualCamera vc)
  {
    double[][] p = vc.calibrationMatrix().getArray();
    Camera cam = new Camera(p, new Point(0, 0), 1);
    Camera shown = new Camera(p, DISPLAY_OFFSET, DISPLAY_SCALE);

    Map<String, Object> c = new TreeMap<String, Object>();
    c.put("name", vc.name);
    c.put("width", vc.getWidth());
    c.put("height", vc.getHeight());
    c.put("P", rows(p));

    List<Object> points = new ArrayList<Object>();
    List<Object> displayPoints = new ArrayList<Object>();
    for (double[] xyz : POINTS) {
      Point3D w = new Point3D(xyz[0], xyz[1], xyz[2]);
      points.add(point(xyz, cam.xyz2uv(w)));
      displayPoints.add(point(xyz, shown.xyz2uv(w)));
    }
    c.put("points", points);

    List<Object> lines = new ArrayList<Object>();
    List<Object> plane = new ArrayList<Object>();
    for (int[] px : PIXELS) {
      Point uv = new Point(px[0], px[1]);
      Line3D ray = cam.uv2line(uv);
      Map<String, Object> l = new TreeMap<String, Object>();
      l.put("uv", ints(px));
      l.put("a", xyz(ray.getStart()));
      l.put("b", xyz(ray.getEnd()));
      lines.add(l);

      Map<String, Object> pl = new TreeMap<String, Object>();
      pl.put("uv", ints(px));
      pl.put("z0", xyz(cam.uv2xyzPlane(uv, 0, 0, 1, 0)));
      pl.put("z40", xyz(cam.uv2xyzPlane(uv, 0, 0, 1, -40)));
      plane.add(pl);
    }
    c.put("lines", lines);
    c.put("plane", plane);

    // P after the display transform: u' = scale*u + offset.x, so row0' = scale*row0 + offset.x*row2
    double[][] dp = new double[3][4];
    for (int j = 0; j < 4; j++) {
      dp[0][j] = DISPLAY_SCALE * p[0][j] + DISPLAY_OFFSET.x * p[2][j];
      dp[1][j] = DISPLAY_SCALE * p[1][j] + DISPLAY_OFFSET.y * p[2][j];
      dp[2][j] = p[2][j];
    }
    Map<String, Object> d = new TreeMap<String, Object>();
    d.put("scale", DISPLAY_SCALE);
    d.put("offset", ints(new int[] {DISPLAY_OFFSET.x, DISPLAY_OFFSET.y}));
    d.put("P", rows(dp));
    d.put("points", displayPoints);
    c.put("display", d);
    return c;
  }

  private static Map<String, Object> point(double[] xyz, Point uv)
  {
    Map<String, Object> m = new TreeMap<String, Object>();
    m.put("xyz", doubles(xyz));
    m.put("uv", ints(new int[] {uv.x, uv.y}));
    return m;
  }

  private static List<Object> xyz(Point3D p)
  {
    return doubles(new double[] {p.x, p.y, p.z});
  }

  private static List<Object> doubles(double[] v)
  {
    List<Object> l = new ArrayList<Object>();
    for (double d : v)
      l.add(d);
    return l;
  }

  private static List<Object> ints(int[] v)
  {
    List<Object> l = new ArrayList<Object>();
    for (int i : v)
      l.add(i);
    return l;
  }

  /**
   * Matrix rows are written to 12 decimals, not 6: the bottom row of P is of the order 1e-4, and
   * 6 decimals would put whole pixels of error into a port that projects with the written matrix.
   */
  private static List<Object> rows(double[][] m)
  {
    List<Object> l = new ArrayList<Object>();
    for (double[] r : m) {
      List<Object> row = new ArrayList<Object>();
      for (double d : r)
        row.add(new Precise(d));
      l.add(row);
    }
    return l;
  }

  private static final class Precise
  {
    final double value;

    Precise(double value) { this.value = value; }
  }

  // a minimal JSON writer: sorted maps, ints as they are, doubles with 6 decimals, short number arrays on one line
  @SuppressWarnings("unchecked")
  private static void json(Object o, StringBuilder sb, int depth)
  {
    if (o instanceof Map) {
      Map<String, Object> m = (Map<String, Object>) o;
      sb.append("{\n");
      int n = 0;
      for (Map.Entry<String, Object> e : m.entrySet()) {
        indent(sb, depth + 1);
        sb.append('"').append(e.getKey()).append("\": ");
        json(e.getValue(), sb, depth + 1);
        sb.append(++n < m.size() ? ",\n" : "\n");
      }
      indent(sb, depth);
      sb.append('}');
    } else if (o instanceof List) {
      List<Object> l = (List<Object>) o;
      boolean flat = true;
      for (Object x : l)
        flat &= !(x instanceof Map) && !(x instanceof List);
      if (flat) {
        sb.append('[');
        for (int i = 0; i < l.size(); i++) {
          if (i > 0)
            sb.append(", ");
          json(l.get(i), sb, depth);
        }
        sb.append(']');
      } else {
        sb.append("[\n");
        for (int i = 0; i < l.size(); i++) {
          indent(sb, depth + 1);
          json(l.get(i), sb, depth + 1);
          sb.append(i < l.size() - 1 ? ",\n" : "\n");
        }
        indent(sb, depth);
        sb.append(']');
      }
    } else if (o instanceof Precise) {
      sb.append(fixed(((Precise) o).value, 12));
    } else if (o instanceof Double) {
      sb.append(fixed((Double) o, 6));
    } else if (o instanceof String) {
      sb.append('"').append(((String) o).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    } else {
      sb.append(o);
    }
  }

  private static String fixed(double d, int decimals)
  {
    String s = String.format(Locale.ROOT, "%." + decimals + "f", d);
    return s.matches("-0\\.0+") ? s.substring(1) : s;
  }

  private static void indent(StringBuilder sb, int depth)
  {
    for (int i = 0; i < depth; i++)
      sb.append("  ");
  }
}
