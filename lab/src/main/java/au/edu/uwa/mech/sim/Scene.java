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
    box(new double[] {b.x, b.y, b.z + b.sz / 2},
        new double[] {c, s, 0}, new double[] {-s, c, 0}, new double[] {0, 0, 1},
        b.sx / 2, b.sy / 2, b.sz / 2, b.colour, true);
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
