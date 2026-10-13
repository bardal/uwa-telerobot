package au.edu.uwa.mech.sim;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Software renderer for the virtual workcell: a z-buffered triangle
 * rasteriser with flat shading, 2x supersampling, a gridded table, soft-edged
 * shadows from an overhead light and a webcam-style caption.
 */
public class Renderer
{
  static final int SS = 2;                  // supersampling factor
  static final double NEAR = 10;
  /** Table top extent (mm); the grid covers 0..GRID_MAX in x and y. */
  static final double TABLE_X0 = -220, TABLE_X1 = 720, TABLE_Y0 = -170, TABLE_Y1 = 720, GRID_MAX = 600;
  static final double FLOOR_Z = -760;
  private static final double[] LIGHT = VirtualCamera.norm(new double[] {0.3, 0.55, 1});
  private static final double[] SHADOW_DIR = VirtualCamera.norm(new double[] {0.1, 0.2, 1});
  private static final Color TABLE = new Color(208, 203, 188), FLOOR = new Color(92, 96, 104);

  public BufferedImage render(VirtualCamera cam, World.Snapshot world, String caption)
  {
    int w = cam.getWidth() * SS, h = cam.getHeight() * SS;
    int[] rgb = new int[w * h];
    float[] invz = new float[w * h];
    boolean[] table = new boolean[w * h];
    Scene scene = Scene.of(world);

    background(cam, w, h, rgb, invz, table);
    shadows(cam, scene, w, h, rgb, table);
    for (Scene.Tri t : scene.tris)
      triangle(cam, t, w, h, rgb, invz);

    BufferedImage img = downsample(rgb, w, h);
    stamp(img, cam.name, caption, world.time);
    return img;
  }

  private void background(VirtualCamera cam, int w, int h, int[] rgb, float[] invz, boolean[] table)
  {
    double[] eye = cam.eye;
    for (int v = 0; v < h; v++)
      for (int u = 0; u < w; u++) {
        int i = v * w + u;
        double[] d = cam.rayDirection(u + 0.5, v + 0.5, SS); // forward component is 1, so t is depth
        if (d[2] < 0) {
          double t = -eye[2] / d[2];
          double x = eye[0] + d[0] * t, y = eye[1] + d[1] * t;
          if (x >= TABLE_X0 && x <= TABLE_X1 && y >= TABLE_Y0 && y <= TABLE_Y1) {
            rgb[i] = tableColour(x, y);
            invz[i] = (float) (1 / t);
            table[i] = true;
            continue;
          }
          double tf = (FLOOR_Z - eye[2]) / d[2];
          double fx = eye[0] + d[0] * tf, fy = eye[1] + d[1] * tf;
          boolean tile = (((int) Math.floor(fx / 300) + (int) Math.floor(fy / 300)) & 1) == 0;
          rgb[i] = shade(FLOOR, tile ? 1.0 : 0.92);
          invz[i] = (float) (1 / tf);
        } else {
          double g = Math.max(0, Math.min(1, d[2] * 2)); // wall, lighter towards the top
          rgb[i] = rgb(150 + 40 * g, 158 + 40 * g, 168 + 40 * g);
        }
      }
  }

  private static int tableColour(double x, double y)
  {
    int c = TABLE.getRGB();
    if (x >= 0 && x <= GRID_MAX && y >= 0 && y <= GRID_MAX) {
      double mx = Math.abs(x - Math.rint(x / 50) * 50), my = Math.abs(y - Math.rint(y / 50) * 50);
      boolean major = (Math.abs(x - Math.rint(x / 100) * 100) < 1.6) || (Math.abs(y - Math.rint(y / 100) * 100) < 1.6);
      if (Math.abs(x) < 2.5 && y >= 0)
        return rgb(170, 60, 50);           // x = 0 edge
      if (Math.abs(y) < 2.5 && x >= 0)
        return rgb(50, 80, 160);           // y = 0 edge
      if (major)
        return shade(TABLE, 0.78);
      if (mx < 0.9 || my < 0.9)
        return shade(TABLE, 0.9);
    }
    return c;
  }

  /** Darken table pixels lying in the shadow of shadow-casting triangles. */
  private void shadows(VirtualCamera cam, Scene scene, int w, int h, int[] rgb, boolean[] table)
  {
    boolean[] dark = new boolean[w * h];
    for (Scene.Tri t : scene.tris) {
      if (!t.shadow)
        continue;
      double[][] p = {onTable(t.a), onTable(t.b), onTable(t.c)};
      double[][] s = new double[3][];
      for (int k = 0; k < 3; k++)
        if ((s[k] = screen(cam, p[k])) == null)
          break;
      if (s[2] == null)
        continue;
      fill(s, w, h, (i, z) -> dark[i] = true);
    }
    for (int i = 0; i < dark.length; i++)
      if (dark[i] && table[i])
        rgb[i] = shadeRGB(rgb[i], 0.62);
  }

  private static double[] onTable(double[] p)
  {
    double t = Math.max(p[2], 0) / SHADOW_DIR[2];
    return new double[] {p[0] - SHADOW_DIR[0] * t, p[1] - SHADOW_DIR[1] * t, 0.2};
  }

  private void triangle(VirtualCamera cam, Scene.Tri t, int w, int h, int[] rgb, float[] invz)
  {
    double[] sa = screen(cam, t.a), sb = screen(cam, t.b), sc = screen(cam, t.c);
    if (sa == null || sb == null || sc == null)
      return;
    double[] n = VirtualCamera.cross(VirtualCamera.sub(t.b, t.a), VirtualCamera.sub(t.c, t.a));
    double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
    if (len == 0)
      return;
    double[] toEye = VirtualCamera.sub(cam.eye, t.a);
    double facing = n[0] * toEye[0] + n[1] * toEye[1] + n[2] * toEye[2];
    double sign = facing < 0 ? -1 : 1; // two-sided lighting
    double lambert = sign * (n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]) / len;
    final int colour = shade(t.colour, 0.5 + 0.5 * Math.max(0, lambert));
    fill(new double[][] {sa, sb, sc}, w, h, (i, z) -> {
      if (z > invz[i]) {
        invz[i] = (float) z;
        rgb[i] = colour;
      }
    });
  }

  /** Supersampled screen position (u, v, 1/depth), or null if behind the near plane. */
  private static double[] screen(VirtualCamera cam, double[] p)
  {
    double[] c = cam.toCamera(p[0], p[1], p[2]);
    if (c[2] < NEAR)
      return null;
    return new double[] {(cam.focal() * c[0] / c[2] + cam.centreU()) * SS,
                         (cam.focal() * c[1] / c[2] + cam.centreV()) * SS, 1 / c[2]};
  }

  interface PixelSink { void put(int index, double invDepth); }

  /** Scan-convert a screen triangle, interpolating 1/depth (which is linear in screen space). */
  private static void fill(double[][] s, int w, int h, PixelSink sink)
  {
    double x0 = s[0][0], y0 = s[0][1], x1 = s[1][0], y1 = s[1][1], x2 = s[2][0], y2 = s[2][1];
    double area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
    if (Math.abs(area) < 1e-9)
      return;
    int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
    int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
    int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
    int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
    for (int y = minY; y <= maxY; y++) {
      double py = y + 0.5;
      for (int x = minX; x <= maxX; x++) {
        double px = x + 0.5;
        double w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area;
        double w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area;
        double w2 = 1 - w0 - w1;
        if (w0 < 0 || w1 < 0 || w2 < 0)
          continue;
        sink.put(y * w + x, w0 * s[0][2] + w1 * s[1][2] + w2 * s[2][2]);
      }
    }
  }

  private static BufferedImage downsample(int[] rgb, int w, int h)
  {
    int ow = w / SS, oh = h / SS;
    BufferedImage img = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < oh; y++)
      for (int x = 0; x < ow; x++) {
        int r = 0, g = 0, b = 0;
        for (int dy = 0; dy < SS; dy++)
          for (int dx = 0; dx < SS; dx++) {
            int c = rgb[(y * SS + dy) * w + x * SS + dx];
            r += (c >> 16) & 255;
            g += (c >> 8) & 255;
            b += c & 255;
          }
        int n = SS * SS;
        img.setRGB(x, y, ((r / n) << 16) | ((g / n) << 8) | (b / n));
      }
    return img;
  }

  private static void stamp(BufferedImage img, String name, String caption, long time)
  {
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
    String text = name + "  " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time))
        + (caption == null ? "" : "  " + caption);
    int tw = g.getFontMetrics().stringWidth(text);
    g.setColor(new Color(0, 0, 0, 140));
    int h = img.getHeight();   // bottom-left: the applet draws its own text at the top
    g.fillRect(0, h - 16, tw + 8, 16);
    g.setColor(Color.white);
    g.drawString(text, 4, h - 4);
    g.dispose();
  }

  private static int rgb(double r, double g, double b)
  {
    return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  private static int shade(Color c, double k) { return rgb(c.getRed() * k, c.getGreen() * k, c.getBlue() * k); }

  private static int shadeRGB(int c, double k)
  {
    return rgb(((c >> 16) & 255) * k, ((c >> 8) & 255) * k, (c & 255) * k);
  }

  private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
}
