// Port of overlaid/PointMeasure: click the same point in two camera images and
// it is triangulated (midpoint of the shortest segment between the two rays).
// After the first click the other camera shows the epipolar line in green.

import type { Camera, UV, Vec3 } from '../geometry/camera';
import { drawCrossHairs, project, type Clickable, type Rect } from './types';

/** A line with a start point and a direction vector (Line3D). */
export type Ray = { start: Vec3; dir: Vec3 };

const sub = (a: Vec3, b: Vec3): Vec3 => ({ x: a.x - b.x, y: a.y - b.y, z: a.z - b.z });
const dot = (a: Vec3, b: Vec3): number => a.x * b.x + a.y * b.y + a.z * b.z;
const at = (r: Ray, t: number): Vec3 => ({
  x: r.start.x + r.dir.x * t,
  y: r.start.y + r.dir.y * t,
  z: r.start.z + r.dir.z * t,
});

export function rayThrough(cam: Camera, uv: UV): Ray {
  const l = cam.uv2line(uv);
  return { start: l.a, dir: sub(l.b, l.a) };
}

/** Line3D.midPoint: midpoint of the shortest segment joining two lines. */
export function midPoint(r1: Ray, r2: Ray): Vec3 {
  const w = sub(r1.start, r2.start);
  const a = dot(r1.dir, r1.dir);
  const b = dot(r1.dir, r2.dir);
  const c = dot(r2.dir, r2.dir);
  const d = dot(r1.dir, w);
  const e = dot(r2.dir, w);
  const denom = a * c - b * b;
  const [s, t] =
    Math.abs(denom) < 1e-12 * a * c ? [0, e / c] : [(b * e - c * d) / denom, (a * e - b * d) / denom];
  const p = at(r1, s);
  const q = at(r2, t);
  return { x: (p.x + q.x) / 2, y: (p.y + q.y) / 2, z: (p.z + q.z) / 2 };
}

function closestPointOnLineTo(r: Ray, p: Vec3): Vec3 {
  const a = dot(r.dir, r.dir);
  return a === 0 ? { ...r.start } : at(r, dot(sub(p, r.start), r.dir) / a);
}

const sameCamera = (cam: Camera, r: Ray): boolean => {
  const c = cam.centre();
  return Math.hypot(c.x - r.start.x, c.y - r.start.y, c.z - r.start.z) < 1e-6;
};

export class PointMeasure implements Clickable {
  private _enabled = false;
  private pd: Ray | null = null;
  triangulated: Vec3 | null = null;
  private clip: Rect | null = null;
  onMeasured: (p: Vec3) => void = () => undefined;
  /** Where Java wrote to TheConsole. */
  onMessage: (s: string) => void = () => undefined;
  onChange: () => void = () => undefined;

  get enabled(): boolean {
    return this._enabled;
  }
  set enabled(v: boolean) {
    if (this._enabled !== v) {
      this._enabled = v;
      this.pd = null;
      this.triangulated = null;
      this.onChange();
    }
  }

  setClip(r: Rect): void {
    this.clip = r;
  }

  start(): void {
    this.clear();
    this.onMessage('Click on the point to measure');
  }

  clear(): void {
    this.pd = null;
    this.triangulated = null;
    this.onChange();
  }

  draw(ctx: CanvasRenderingContext2D, cam: Camera): void {
    if (!this._enabled) return;
    ctx.lineWidth = 1;
    if (this.triangulated) {
      const p = project(cam, this.triangulated);
      if (p) drawCrossHairs(ctx, p);
      return;
    }
    const pd = this.pd;
    if (!pd) return;
    if (sameCamera(cam, pd)) {
      const p = project(cam, closestPointOnLineTo(pd, { x: 0, y: 0, z: 0 }));
      if (p) drawCrossHairs(ctx, p);
    } else {
      this.drawEpipole(ctx, cam, pd);
    }
  }

  /** The projection of the first ray is a straight line however far it runs behind this camera. */
  private drawEpipole(ctx: CanvasRenderingContext2D, cam: Camera, pd: Ray): void {
    const cr = this.clip;
    if (!cr) return;
    const a = cam.xyz2uv(pd.start);
    const b = cam.xyz2uv(at(pd, 1));
    let dx = b.u - a.u;
    let dy = b.v - a.v;
    const len = Math.hypot(dx, dy);
    if (!(len > 0) || !Number.isFinite(len)) return;
    dx /= len;
    dy /= len;
    // the nearest point on the epipole to the centre of the clip rect, then out +- hyp
    const cx = cr.x + cr.width / 2;
    const cy = cr.y + cr.height / 2;
    const t = (cx - a.u) * dx + (cy - a.v) * dy;
    const px = a.u + t * dx;
    const py = a.v + t * dy;
    const hyp = Math.hypot(cr.width, cr.height);
    ctx.strokeStyle = '#00ff00';
    ctx.beginPath();
    ctx.moveTo(Math.round(px + dx * hyp), Math.round(py + dy * hyp));
    ctx.lineTo(Math.round(px - dx * hyp), Math.round(py - dy * hyp));
    ctx.stroke();
  }

  click(uv: UV, cam: Camera): boolean {
    if (!this._enabled) return false;
    if (this.triangulated) {
      this.pd = null;
      this.triangulated = null;
    }
    const ray = rayThrough(cam, uv);
    if (this.pd === null) {
      this.pd = ray;
      this.onMessage('Now click the same point in a second image');
    } else if (sameCamera(cam, this.pd)) {
      this.pd = ray;
    } else {
      const p = midPoint(this.pd, ray);
      this.triangulated = p;
      this.onMessage(`Point location is (${Math.round(p.x)},${Math.round(p.y)},${Math.round(p.z)})`);
      this.onMeasured(p);
    }
    this.onChange();
    return true;
  }
}
