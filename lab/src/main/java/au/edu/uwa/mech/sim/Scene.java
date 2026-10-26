package au.edu.uwa.mech.sim;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Triangle geometry for a world snapshot: a six-axis arm in the style of
 * the ABB IRB 1400 standing behind the table, its parallel gripper, and the
 * blocks. Table coordinates: x and y across the table, z up, mm.
 */
public final class Scene
{
  public static final class Tri
  {
    final double[] a, b, c;
    final Color colour;
    final boolean shadow;

    Tri(double[] a, double[] b, double[] c, Color colour, boolean shadow)
    {
      this.a = a; this.b = b; this.c = c;
      this.colour = colour;
      this.shadow = shadow;
    }
  }

  /** Where the arm stands, and its dimensions (mm). */
  public static double[] BASE = {250, -280, 0};
  static final double SHOULDER_HEIGHT = 470, SHOULDER_OFFSET = 70;
  static final double UPPER_ARM = 460, FOREARM = 560;
  /** Tool point to wrist centre along the tool axis. */
  static final double TOOL_LENGTH = 150;
  /** Radius of the half-round opening under an arch, mm. */
  static final double ARCH_RADIUS = 18;

  static final Color ABB_ORANGE = new Color(232, 116, 28);
  static final Color DARK = new Color(70, 70, 75);
  static final Color STEEL = new Color(170, 172, 178);
  static final Color PEDESTAL = new Color(110, 110, 115);

  final List<Tri> tris = new ArrayList<Tri>();

  private Scene() {}

  public static Scene of(World.Snapshot w)
  {
    Scene s = new Scene();
    for (Block b : w.blocks)
      s.addBlock(b);
    s.addArm(w.pose, w.fingerGap);
    return s;
  }

  private void addBlock(Block b)
  {
    double c = Math.cos(b.yaw), s = Math.sin(b.yaw);
    double[] u = {c, s, 0}, v = {-s, c, 0}, up = {0, 0, 1};
    double hx = b.sx / 2, h = b.sz;
    switch (b.shape) {
      case CYLINDER:
        cylinder(new double[] {b.x, b.y, b.z}, new double[] {b.x, b.y, b.z + h}, Math.min(b.sx, b.sy) / 2, b.colour, true);
        break;
      case ROOF:
        prism(b, u, v, new double[][] {{-hx, 0}, {hx, 0}, {0, h}}, new int[][] {{0, 1, 2}});
        break;
      case ARCH:
        arch(b, u, v, hx, h);
        break;
      default:
        box(new double[] {b.x, b.y, b.z + h / 2}, u, v, up, hx, b.sy / 2, h / 2, b.colour, true);
    }
  }

  /**
   * An arch: a block with a half-round opening of {@link #ARCH_RADIUS} under its middle. The end
   * faces are a pillar quad either side and a strip above each segment of the curve.
   */
  private void arch(Block b, double[] u, double[] v, double hx, double h)
  {
    int n = 10;
    double r = ARCH_RADIUS;
    // outline: up the left end, across the top, down the right end, along the floor to the opening, round the curve
    double[][] outline = new double[4 + n + 1][];
    outline[0] = new double[] {-hx, 0};
    outline[1] = new double[] {-hx, h};
    outline[2] = new double[] {hx, h};
    outline[3] = new double[] {hx, 0};
    for (int i = 0; i <= n; i++) {
      double a = Math.PI * i / n;   // from the right foot of the opening over the top to the left foot
      outline[4 + i] = new double[] {r * Math.cos(a), r * Math.sin(a)};
    }
    // each end face: a quad for either pillar, and a strip above each segment of the curve
    List<double[][]> quads = new ArrayList<double[][]>();
    quads.add(new double[][] {{-hx, 0}, {-hx, h}, {-r, h}, {-r, 0}});
    quads.add(new double[][] {{r, 0}, {r, h}, {hx, h}, {hx, 0}});
    for (int i = 0; i < n; i++) {
      double[] a = outline[4 + i], c = outline[4 + i + 1];
      quads.add(new double[][] {a, {a[0], h}, {c[0], h}, c});
    }
    for (double d : new double[] {-b.sy / 2, b.sy / 2})
      for (double[][] q : quads)
        quad(at(b, u, v, q[0], d), at(b, u, v, q[1], d), at(b, u, v, q[2], d), at(b, u, v, q[3], d), b.colour, true);
    sides(b, u, v, outline);
  }

  /** A shape with a constant cross-section (profile in (along x, height) coordinates) through the block's depth. */
  private void prism(Block b, double[] u, double[] v, double[][] profile, int[][] capTris)
  {
    prismCaps(b, u, v, profile, capTris);
    sides(b, u, v, profile);
  }

  private void prismCaps(Block b, double[] u, double[] v, double[][] pts, int[][] capTris)
  {
    for (double d : new double[] {-b.sy / 2, b.sy / 2})
      for (int[] t : capTris)
        tris.add(new Tri(at(b, u, v, pts[t[0]], d), at(b, u, v, pts[t[1]], d), at(b, u, v, pts[t[2]], d), b.colour, true));
  }

  /** The faces swept by each edge of a closed outline through the block's depth. */
  private void sides(Block b, double[] u, double[] v, double[][] outline)
  {
    double d = b.sy / 2;
    for (int i = 0; i < outline.length; i++) {
      double[] p = outline[i], q = outline[(i + 1) % outline.length];
      quad(at(b, u, v, p, -d), at(b, u, v, q, -d), at(b, u, v, q, d), at(b, u, v, p, d), b.colour, true);
    }
  }

  /** The world point of profile point p (along u, height) at depth d along v. */
  private static double[] at(Block b, double[] u, double[] v, double[] p, double d)
  {
    return new double[] {b.x + u[0] * p[0] + v[0] * d, b.y + u[1] * p[0] + v[1] * d, b.z + p[1]};
  }

  private void addArm(RobotPose p, double gap)
  {
    double s = p.spinRadians(), t = p.tiltRadians();
    // gripper frame: a = tool axis (tip towards wrist), f = finger direction, g = a x f
    double[] a = {Math.sin(t) * Math.cos(s), Math.sin(t) * Math.sin(s), Math.cos(t)};
    double[] f = {Math.cos(t) * Math.cos(s), Math.cos(t) * Math.sin(s), -Math.sin(t)};
    double[] g = VirtualCamera.cross(a, f);
    double[] tip = {p.x, p.y, p.z};

    // fingers and palm
    for (int side = -1; side <= 1; side += 2)
      box(along(tip, a, 32, f, side * (gap / 2 + 5)), f, g, a, 5, 13, 32, STEEL, true);
    box(along(tip, a, 76, f, 0), f, g, a, gap / 2 + 14, 17, 12, DARK, true);
    double[] flangeBottom = along(tip, a, 88, f, 0);
    double[] wrist = along(tip, a, TOOL_LENGTH, f, 0);
    cylinder(flangeBottom, wrist, 26, DARK, true);

    // inverse kinematics for the wrist centre: base yaw, then a 2-link arm in that vertical plane
    double yaw = Math.atan2(wrist[1] - BASE[1], wrist[0] - BASE[0]);
    double[] radial = {Math.cos(yaw), Math.sin(yaw), 0};
    double[] shoulder = {BASE[0] + radial[0] * SHOULDER_OFFSET, BASE[1] + radial[1] * SHOULDER_OFFSET,
                         BASE[2] + SHOULDER_HEIGHT};
    double rr = Math.hypot(wrist[0] - shoulder[0], wrist[1] - shoulder[1]);
    double hh = wrist[2] - shoulder[2];
    double d = Math.min(Math.hypot(rr, hh), UPPER_ARM + FOREARM - 1);
    double elev = Math.atan2(hh, rr);
    double cosB = (UPPER_ARM * UPPER_ARM + d * d - FOREARM * FOREARM) / (2 * UPPER_ARM * d);
    double q1 = elev + Math.acos(Math.max(-1, Math.min(1, cosB))); // elbow up
    double[] elbow = {shoulder[0] + radial[0] * UPPER_ARM * Math.cos(q1),
                      shoulder[1] + radial[1] * UPPER_ARM * Math.cos(q1),
                      shoulder[2] + UPPER_ARM * Math.sin(q1)};
    double[] wristReached = {shoulder[0] + radial[0] * d * Math.cos(elev), shoulder[1] + radial[1] * d * Math.cos(elev),
                             shoulder[2] + d * Math.sin(elev)};
    double[] side = {-radial[1], radial[0], 0};

    // pedestal to the floor, base, turret
    cylinder(new double[] {BASE[0], BASE[1], -760}, new double[] {BASE[0], BASE[1], 0}, 150, PEDESTAL, false);
    cylinder(new double[] {BASE[0], BASE[1], 0}, new double[] {BASE[0], BASE[1], 140}, 130, DARK, true);
    box(new double[] {BASE[0] + radial[0] * 40, BASE[1] + radial[1] * 40, 280},
        radial, side, new double[] {0, 0, 1}, 120, 95, 140, ABB_ORANGE, true);
    cylinder(along(shoulder, side, 85, radial, 0), along(shoulder, side, -85, radial, 0), 70, DARK, true);
    // upper arm, elbow, forearm, wrist housing
    link(shoulder, elbow, side, 55, 45, ABB_ORANGE);
    cylinder(along(elbow, side, 60, radial, 0), along(elbow, side, -60, radial, 0), 55, DARK, true);
    link(elbow, wristReached, side, 42, 38, ABB_ORANGE);
    cylinder(along(wristReached, side, 40, radial, 0), along(wristReached, side, -40, radial, 0), 34, ABB_ORANGE, true);
    // if the target is out of reach the wrist stops short; join it to the tool anyway
    if (Math.hypot(Math.hypot(wristReached[0] - wrist[0], wristReached[1] - wrist[1]), wristReached[2] - wrist[2]) > 1)
      cylinder(wristReached, wrist, 20, DARK, true);
  }

  /** point + u*du + v*dv */
  private static double[] along(double[] p, double[] u, double du, double[] v, double dv)
  {
    return new double[] {p[0] + u[0] * du + v[0] * dv, p[1] + u[1] * du + v[1] * dv, p[2] + u[2] * du + v[2] * dv};
  }

  /** A box between two points, with given half-width (along side) and half-depth. */
  private void link(double[] from, double[] to, double[] side, double halfWidth, double halfDepth, Color c)
  {
    double[] axis = VirtualCamera.sub(to, from);
    double len = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
    axis = VirtualCamera.norm(axis);
    double[] depth = VirtualCamera.norm(VirtualCamera.cross(axis, side));
    double[] sideOrtho = VirtualCamera.cross(depth, axis);
    double[] mid = {(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2};
    box(mid, axis, sideOrtho, depth, len / 2, halfWidth, halfDepth, c, true);
  }

  /** Oriented box: centre, unit axes u v w and half extents along them. */
  private void box(double[] c, double[] u, double[] v, double[] w, double hu, double hv, double hw,
                   Color col, boolean shadow)
  {
    double[][] k = new double[8][];
    for (int i = 0; i < 8; i++) {
      double su = (i & 1) == 0 ? -hu : hu, sv = (i & 2) == 0 ? -hv : hv, sw = (i & 4) == 0 ? -hw : hw;
      k[i] = new double[] {c[0] + u[0] * su + v[0] * sv + w[0] * sw,
                           c[1] + u[1] * su + v[1] * sv + w[1] * sw,
                           c[2] + u[2] * su + v[2] * sv + w[2] * sw};
    }
    int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
    for (int[] q : faces)
      quad(k[q[0]], k[q[1]], k[q[2]], k[q[3]], col, shadow);
  }

  private void cylinder(double[] from, double[] to, double radius, Color col, boolean shadow)
  {
    double[] axis = VirtualCamera.norm(VirtualCamera.sub(to, from));
    double[] ref = Math.abs(axis[2]) < 0.9 ? new double[] {0, 0, 1} : new double[] {1, 0, 0};
    double[] u = VirtualCamera.norm(VirtualCamera.cross(axis, ref));
    double[] v = VirtualCamera.cross(axis, u);
    int n = 20;
    double[][] bottom = new double[n][], top = new double[n][];
    for (int i = 0; i < n; i++) {
      double ang = 2 * Math.PI * i / n;
      double cu = Math.cos(ang) * radius, sv = Math.sin(ang) * radius;
      bottom[i] = along(from, u, cu, v, sv);
      top[i] = along(to, u, cu, v, sv);
    }
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      quad(bottom[i], bottom[j], top[j], top[i], col, shadow);
      tris.add(new Tri(from, bottom[j], bottom[i], col, shadow));
      tris.add(new Tri(to, top[i], top[j], col, shadow));
    }
  }

  private void quad(double[] a, double[] b, double[] c, double[] d, Color col, boolean shadow)
  {
    tris.add(new Tri(a, b, c, col, shadow));
    tris.add(new Tri(a, c, d, col, shadow));
  }
}
