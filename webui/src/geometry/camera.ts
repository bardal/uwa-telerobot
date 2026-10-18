// Port of au.edu.uwa.mech.gui.overlaid.Camera: a pinhole camera described by a
// 3x4 DLT calibration matrix P (row-major, 12 numbers). A world point (x, y, z)
// maps to (u, v) = (P0.X / P2.X, P1.X / P2.X) with X = (x, y, z, 1).

export type Vec3 = { x: number; y: number; z: number };
export type UV = { u: number; v: number };
export type P34 = number[]; // 12, row-major

/**
 * The matrix for display coordinates: P' = [[s,0,ox],[0,s,oy],[0,0,1]] . P, so
 * u' = s*u + ox and v' = s*v + oy. Same as CalibratedCanvas.getCalibrationMatrix.
 */
export function displayMatrix(p: P34, scale: number, ox: number, oy: number): P34 {
  const out = new Array<number>(12);
  for (let j = 0; j < 4; j++) {
    out[j] = scale * p[j]! + ox * p[8 + j]!;
    out[4 + j] = scale * p[4 + j]! + oy * p[8 + j]!;
    out[8 + j] = p[8 + j]!;
  }
  return out;
}

type M3 = [number, number, number, number, number, number, number, number, number];

function invert3(m: M3): M3 {
  const [a, b, c, d, e, f, g, h, i] = m;
  const det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
  if (det === 0) throw new Error('calibration matrix is singular');
  return [
    (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
    (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
    (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det,
  ];
}

function mul3(m: M3, v: [number, number, number]): [number, number, number] {
  return [
    m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
    m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
    m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
  ];
}

/**
 * The point nearest two rays (each through a and b): the midpoint of their
 * shortest connecting segment, as the 1996 image server did for two clicked
 * images. Null when the rays are (nearly) parallel.
 */
export function triangulate(r1: { a: Vec3; b: Vec3 }, r2: { a: Vec3; b: Vec3 }): Vec3 | null {
  const d1 = { x: r1.b.x - r1.a.x, y: r1.b.y - r1.a.y, z: r1.b.z - r1.a.z };
  const d2 = { x: r2.b.x - r2.a.x, y: r2.b.y - r2.a.y, z: r2.b.z - r2.a.z };
  const w = { x: r1.a.x - r2.a.x, y: r1.a.y - r2.a.y, z: r1.a.z - r2.a.z };
  const dot = (p: Vec3, q: Vec3) => p.x * q.x + p.y * q.y + p.z * q.z;
  const a = dot(d1, d1), b = dot(d1, d2), c = dot(d2, d2), d = dot(d1, w), e = dot(d2, w);
  const den = a * c - b * b;
  if (Math.abs(den) < 1e-9 * a * c) return null;
  const s = (b * e - c * d) / den;
  const t = (a * e - b * d) / den;
  const p = { x: r1.a.x + s * d1.x, y: r1.a.y + s * d1.y, z: r1.a.z + s * d1.z };
  const q = { x: r2.a.x + t * d2.x, y: r2.a.y + t * d2.y, z: r2.a.z + t * d2.z };
  return { x: (p.x + q.x) / 2, y: (p.y + q.y) / 2, z: (p.z + q.z) / 2 };
}

export class Camera {
  private readonly p: P34;
  private readonly mInv: M3;
  private readonly c: Vec3;

  constructor(p: P34) {
    if (p.length !== 12) throw new Error('calibration matrix must have 12 entries');
    this.p = p.slice();
    this.mInv = invert3([p[0]!, p[1]!, p[2]!, p[4]!, p[5]!, p[6]!, p[8]!, p[9]!, p[10]!]);
    const o = mul3(this.mInv, [-p[3]!, -p[7]!, -p[11]!]);
    this.c = { x: o[0], y: o[1], z: o[2] };
  }

  /** The optical centre: P * (centre, 1) = 0. */
  centre(): Vec3 {
    return { ...this.c };
  }

  /** Sub-pixel projection in this camera's coordinates (no rounding). */
  xyz2uv(w: Vec3): UV {
    const p = this.p;
    const u = p[0]! * w.x + p[1]! * w.y + p[2]! * w.z + p[3]!;
    const v = p[4]! * w.x + p[5]! * w.y + p[6]! * w.z + p[7]!;
    const s = p[8]! * w.x + p[9]! * w.y + p[10]! * w.z + p[11]!;
    return { u: u / s, v: v / s };
  }

  /** The homogeneous w of the projection of w (P2.X). Zero at the optical centre. */
  depth(w: Vec3): number {
    const p = this.p;
    return p[8]! * w.x + p[9]! * w.y + p[10]! * w.z + p[11]!;
  }

  /**
   * True if w is in front of the camera (homogeneous w > 0). xyz2uv mirrors points
   * behind the camera, so overlays check this before drawing. Assumes the
   * calibration is normalised so points in front have w > 0, as the lab's are.
   */
  inFront(w: Vec3): boolean {
    return this.depth(w) > 0;
  }

  /** Java's `new Point((int) Math.round(..))`: round half up, like Math.round. */
  toPoint(uv: UV): { x: number; y: number } {
    return { x: Math.round(uv.u), y: Math.round(uv.v) };
  }

  /** The ray through the optical centre that images to uv: a is the centre, b a point along the ray. */
  uv2line(uv: UV): { a: Vec3; b: Vec3 } {
    const d = mul3(this.mInv, [uv.u, uv.v, 1]);
    return { a: { ...this.c }, b: { x: this.c.x + d[0], y: this.c.y + d[1], z: this.c.z + d[2] } };
  }

  /** Where the ray through uv meets the plane a*x + b*y + c*z + d = 0 (null if parallel). */
  uv2xyzPlane(uv: UV, a: number, b: number, c: number, d: number): Vec3 | null {
    const ray = this.uv2line(uv);
    const dir = { x: ray.b.x - ray.a.x, y: ray.b.y - ray.a.y, z: ray.b.z - ray.a.z };
    const denom = a * dir.x + b * dir.y + c * dir.z;
    if (Math.abs(denom) < 1e-12) return null;
    const t = -(a * ray.a.x + b * ray.a.y + c * ray.a.z + d) / denom;
    return { x: ray.a.x + t * dir.x, y: ray.a.y + t * dir.y, z: ray.a.z + t * dir.z };
  }

  /**
   * Where the ray through uv meets the surface z = surfaceZ(x, y), found by
   * repeatedly intersecting the horizontal plane at the surface height under
   * the previous guess. Null if the ray never reaches it.
   */
  uv2xyzSurface(uv: UV, surfaceZ: (x: number, y: number) => number): Vec3 | null {
    let hit = this.uv2xyzPlane(uv, 0, 0, 1, 0);
    for (let i = 0; i < 50 && hit; i++) {
      const next = this.uv2xyzPlane(uv, 0, 0, 1, -surfaceZ(hit.x, hit.y));
      if (!next) return null;
      const moved = Math.hypot(next.x - hit.x, next.y - hit.y, next.z - hit.z);
      hit = next;
      if (moved < 1e-6) break;
    }
    return hit;
  }
}
