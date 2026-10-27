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

  /** Where the arm stands (axis 1 at the base's underside). */
  public static double[] BASE = {250, -280, 0};
  // the ABB IRB 1400's dimensions in mm, from its product specification (3HAC 9376-1, figure 3)
  /** Axis 2 above the base and out from axis 1. */
  static final double SHOULDER_HEIGHT = 475, SHOULDER_OFFSET = 150;
  /** Axis 2 to axis 3. */
  static final double LOWER_ARM = 600;
  /** The forearm axis runs FOREARM_RISE above axis 3, FOREARM forward to the wrist centre and FOREARM_BACK behind. */
  static final double FOREARM = 720, FOREARM_RISE = 120, FOREARM_BACK = 170;
  /** Wrist centre (axis 5) to the mounting flange. */
  static final double FLANGE = 85;
  /** Tool point to the flange: the gripper. */
  static final double GRIPPER = 88;
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

  /** Where the arm's joints are for a pose. Directions are unit vectors. */
  static final class Joints
  {
    /** The tool frame: a = tool axis (tip towards wrist), f = finger direction, g = a x f. */
    double[] a, f, g, tip;
    /** The wrist centre the tool needs, and the one the arm gets to (short of it when out of reach). */
    double[] wrist, wristReached;
    /** Axis 2 and axis 3. */
    double[] shoulder, elbow;
    /** The arm's vertical plane: out from axis 1, and across it. */
    double[] radial, side;
    /** Along the forearm axis, and square to it in the arm's plane (up when the forearm is level). */
    double[] forearm, normal;
  }

  /** Inverse kinematics for the wrist centre: axis 1 turns to face it, then axes 2 and 3 reach it elbow up. */
  static Joints joints(RobotPose p)
  {
    Joints j = new Joints();
    double s = p.spinRadians(), t = p.tiltRadians();
    j.a = new double[] {Math.sin(t) * Math.cos(s), Math.sin(t) * Math.sin(s), Math.cos(t)};
    j.f = new double[] {Math.cos(t) * Math.cos(s), Math.cos(t) * Math.sin(s), -Math.sin(t)};
    j.g = VirtualCamera.cross(j.a, j.f);
    j.tip = new double[] {p.x, p.y, p.z};
    j.wrist = along(j.tip, j.a, GRIPPER + FLANGE, j.f, 0);

    double yaw = Math.atan2(j.wrist[1] - BASE[1], j.wrist[0] - BASE[0]);
    j.radial = new double[] {Math.cos(yaw), Math.sin(yaw), 0};
    j.side = new double[] {-j.radial[1], j.radial[0], 0};
    j.shoulder = new double[] {BASE[0] + j.radial[0] * SHOULDER_OFFSET, BASE[1] + j.radial[1] * SHOULDER_OFFSET,
                               BASE[2] + SHOULDER_HEIGHT};
    // in the arm's plane: out from axis 2 (negative when the wrist is behind it) and up
    double rr = Math.hypot(j.wrist[0] - BASE[0], j.wrist[1] - BASE[1]) - SHOULDER_OFFSET;
    double hh = j.wrist[2] - j.shoulder[2];
    double reach = Math.hypot(FOREARM, FOREARM_RISE);
    double d = Math.min(Math.hypot(rr, hh), LOWER_ARM + reach - 1);
    double elev = Math.atan2(hh, rr);
    double cosB = (LOWER_ARM * LOWER_ARM + d * d - reach * reach) / (2 * LOWER_ARM * d);
    double q2 = elev + Math.acos(Math.max(-1, Math.min(1, cosB)));
    j.elbow = inPlane(j, j.shoulder, LOWER_ARM * Math.cos(q2), LOWER_ARM * Math.sin(q2));
    j.wristReached = inPlane(j, j.shoulder, d * Math.cos(elev), d * Math.sin(elev));
    // the forearm axis is the elbow-to-wrist line turned down by the angle its rise makes
    double q3 = Math.atan2(j.wristReached[2] - j.elbow[2],
                           (j.wristReached[0] - j.elbow[0]) * j.radial[0] + (j.wristReached[1] - j.elbow[1]) * j.radial[1])
                - Math.atan2(FOREARM_RISE, FOREARM);
    j.forearm = inPlane(j, new double[3], Math.cos(q3), Math.sin(q3));
    j.normal = inPlane(j, new double[3], -Math.sin(q3), Math.cos(q3));
    return j;
  }

  /** p moved out along the arm's radial direction and up. */
  private static double[] inPlane(Joints j, double[] p, double out, double up)
  {
    return new double[] {p[0] + j.radial[0] * out, p[1] + j.radial[1] * out, p[2] + up};
  }

  /** The IRB 1400: a fixed base on a pedestal, the turning frame with axis 2's round gearbox, the lower arm with the
   *  parallel bar behind it, the upper arm's motor housing and slim forearm, the wrist, then the gripper. */
  private void addArm(RobotPose p, double gap)
  {
    Joints j = joints(p);
    double[] a = j.a, f = j.f, g = j.g, tip = j.tip, radial = j.radial, side = j.side;
    double[] x = {1, 0, 0}, y = {0, 1, 0}, up = {0, 0, 1};

    // fingers and palm, up to the flange; the wrist's tilting part above it
    for (int s = -1; s <= 1; s += 2)
      box(along(tip, a, 32, f, s * (gap / 2 + 5)), f, g, a, 5, 13, 32, STEEL, true);
    box(along(tip, a, 76, f, 0), f, g, a, gap / 2 + 14, 17, 12, DARK, true);
    double[] flange = along(tip, a, GRIPPER, f, 0), flangeTop = along(flange, a, 12, f, 0);
    cylinder(flange, flangeTop, 34, DARK, true);
    cylinder(flangeTop, j.wrist, 28, ABB_ORANGE, true);

    // pedestal to the floor, then the fixed base plate (620 x 450)
    box(new double[] {BASE[0], BASE[1] - 40, Renderer.FLOOR_Z / 2}, x, y, up, 270, 200, -Renderer.FLOOR_Z / 2, PEDESTAL, false);
    box(new double[] {BASE[0], BASE[1] - 40, BASE[2] + 25}, x, y, up, 310, 225, 25, ABB_ORANGE, true);
    // the frame turning on axis 1, its motor at the back, and the support for axis 2's round gearbox
    cylinder(new double[] {BASE[0], BASE[1], BASE[2] + 50}, new double[] {BASE[0], BASE[1], BASE[2] + 150}, 200, ABB_ORANGE, true);
    double[] frame = along(BASE, radial, -30, side, 0);
    box(new double[] {frame[0], frame[1], BASE[2] + 265}, radial, side, up, 110, 95, 115, ABB_ORANGE, true);
    double[] motor = along(BASE, radial, -80, side, -45);
    cylinder(new double[] {motor[0], motor[1], BASE[2] + 380}, new double[] {motor[0], motor[1], BASE[2] + 520}, 50, DARK, true);
    double[] support = along(BASE, radial, 75, side, 120);
    box(new double[] {support[0], support[1], BASE[2] + 312}, radial, side, up, 75, 50, 162, ABB_ORANGE, true);
    cylinder(along(j.shoulder, side, 70, radial, 0), along(j.shoulder, side, 175, radial, 0), 115, ABB_ORANGE, true);
    cylinder(along(j.shoulder, side, 175, radial, 0), along(j.shoulder, side, 200, radial, 0), 70, DARK, true);

    // lower arm, and the parallel bar behind it that turns the upper arm
    link(j.shoulder, j.elbow, side, 55, 50, ABB_ORANGE);
    double[] back = {-j.forearm[0], -j.forearm[1], -j.forearm[2]};
    double[] crankLow = along(j.shoulder, side, -95, back, 0), crankHigh = along(j.elbow, side, -95, back, 0);
    double[] rodLow = along(crankLow, back, 150, side, 0), rodHigh = along(crankHigh, back, 150, side, 0);
    link(crankLow, rodLow, side, 18, 28, ABB_ORANGE);
    link(crankHigh, rodHigh, side, 18, 28, ABB_ORANGE);
    cylinder(rodLow, rodHigh, 17, ABB_ORANGE, true);
    cylinder(along(j.shoulder, side, -70, radial, 0), along(j.shoulder, side, -115, radial, 0), 45, DARK, true);

    // upper arm: the motor housing round axis 3, the forearm tube, the wrist
    double[] forearm = j.forearm, normal = j.normal;
    box(along(j.elbow, forearm, (150 - FOREARM_BACK) / 2, normal, 50), forearm, side, normal,
        (FOREARM_BACK + 150) / 2, 85, 95, ABB_ORANGE, true);
    double[] axis = along(j.elbow, normal, FOREARM_RISE, forearm, 0);
    cylinder(along(axis, forearm, 150, side, 0), along(axis, forearm, 600, side, 0), 50, ABB_ORANGE, true);
    cylinder(along(axis, forearm, 600, side, 0), along(axis, forearm, FOREARM - 40, side, 0), 38, ABB_ORANGE, true);
    cylinder(along(j.wristReached, side, 42, radial, 0), along(j.wristReached, side, -42, radial, 0), 40, ABB_ORANGE, true);
    // if the target is out of reach the wrist stops short; join it to the tool anyway
    if (Math.hypot(Math.hypot(j.wristReached[0] - j.wrist[0], j.wristReached[1] - j.wrist[1]), j.wristReached[2] - j.wrist[2]) > 1)
      cylinder(j.wristReached, j.wrist, 20, DARK, true);
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
