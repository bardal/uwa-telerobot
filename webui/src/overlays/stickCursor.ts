// Port of overlaid/StickCursor: an interactive 3D cursor over a calibrated
// camera image, used to pick a gripper pose by dragging:
//   peak marker  - drag up and down to change height
//   base marker  - drag to move in x and y (on the work surface)
//   plumb line   - drag to move in x and y at the height grabbed
//   x / y lines  - drag to change only y / only x
//   spin needle  - drag round to change spin
//   tilt line    - drag to change tilt

import type { Camera, UV, Vec3 } from '../geometry/camera';
import { Plumbline } from './plumbline';
import { SpinNeedle } from './spinNeedle';
import { TiltNeedle } from './tiltNeedle';
import { spinDeg, spinRad, tiltDeg, type Interactive, type Pose } from './types';

export type { Interactive, Overlay3D, Pose } from './types';

type Drag = 'none' | 'peak' | 'base' | 'height' | 'xLine' | 'yLine' | 'spin' | 'tilt';

const sub = (a: Vec3, b: Vec3): Vec3 => ({ x: a.x - b.x, y: a.y - b.y, z: a.z - b.z });
const dot = (a: Vec3, b: Vec3): number => a.x * b.x + a.y * b.y + a.z * b.z;

/**
 * Z of the point on the vertical line through (x, y) closest to the ray
 * through uv (Line3D.closestPoint, with the vertical line as "this").
 */
function closestZOnVertical(cam: Camera, uv: UV, at: Vec3): number {
  const ray = cam.uv2line(uv);
  const d2 = sub(ray.b, ray.a);
  const d1 = { x: 0, y: 0, z: 1 };
  const w = sub(at, ray.a);
  const a = dot(d1, d1);
  const b = dot(d1, d2);
  const c = dot(d2, d2);
  const d = dot(d1, w);
  const e = dot(d2, w);
  const denom = a * c - b * b;
  const s = Math.abs(denom) < 1e-12 * a * c ? 0 : (b * e - c * d) / denom;
  return at.z + s;
}

export class StickCursor implements Interactive {
  pose: Pose = { x: 0, y: 0, z: 0, spin: 0, tilt: 0 };
  /** Called with the new pose whenever the user's drag changes it. Not called by setPose. */
  onChange: (p: Pose) => void = () => undefined;
  /** Recommended CSS cursor for the containing element. */
  cursor = 'default';

  private readonly pc = new Plumbline();
  private readonly sc = new SpinNeedle();
  private readonly tc = new TiltNeedle();
  private dragging: Drag = 'none';
  private grabHeight = 0;
  private _visible = true;

  get visible(): boolean {
    return this._visible;
  }
  set visible(v: boolean) {
    this._visible = v;
    this.pc.visible = this.sc.visible = this.tc.visible = v;
  }

  /** Move to p without telling onChange (used to follow another camera's cursor). */
  setPose(p: Pose): void {
    this.pose = { ...p };
  }

  setColourNoFocus(c: string): void {
    this.pc.colourNoFocus = this.sc.colourNoFocus = this.tc.colourNoFocus = c;
  }

  get dragActive(): boolean {
    return this.dragging !== 'none';
  }

  draw(ctx: CanvasRenderingContext2D, cam: Camera): void {
    if (!this._visible) return;
    ctx.lineWidth = 1;
    this.pc.draw(ctx, cam, this.pose);
    this.sc.draw(ctx, cam, this.pose);
    this.tc.draw(ctx, cam, this.pose);
  }

  contains(uv: UV, cam: Camera): boolean {
    const m = { x: uv.u, y: uv.v };
    return (
      this.pc.partAt(m, cam, this.pose) !== 'none' ||
      this.sc.contains(m, cam, this.pose) ||
      this.tc.contains(m, cam, this.pose)
    );
  }

  pointerDown(uv: UV, cam: Camera): boolean {
    if (!this._visible) return false;
    const m = { x: uv.u, y: uv.v };
    const part = this.pc.partAt(m, cam, this.pose);
    // markers first, then the orientation handles, then the lines
    if (part === 'peak' || part === 'base') this.dragging = part;
    else if (this.sc.contains(m, cam, this.pose)) this.dragging = 'spin';
    else if (this.tc.contains(m, cam, this.pose)) this.dragging = 'tilt';
    else if (part === 'xLine' || part === 'yLine') this.dragging = part;
    else if (part === 'height') {
      this.dragging = 'height';
      this.grabHeight = this.pose.z * this.pc.heightFraction(m, cam, this.pose);
    } else return false;
    this.pc.focusPart = this.dragging === 'spin' || this.dragging === 'tilt' ? 'none' : part;
    this.sc.focus = this.dragging === 'spin';
    this.tc.focus = this.dragging === 'tilt';
    this.cursor = 'pointer';
    return true;
  }

  pointerMove(uv: UV, cam: Camera): void {
    if (this.dragging === 'none') return;
    const at = this.pose;
    const p: Pose = { ...at };
    const onSurface = cam.uv2xyzPlane(uv, 0, 0, 1, 0);
    const atHeight = cam.uv2xyzPlane(uv, 0, 0, 1, -at.z);
    const s = spinRad(at.spin);
    switch (this.dragging) {
      case 'peak':
        p.z = Math.max(0, closestZOnVertical(cam, uv, at));
        break;
      case 'base':
        if (onSurface) {
          p.x = onSurface.x;
          p.y = onSurface.y;
        }
        break;
      case 'height': {
        const q = cam.uv2xyzPlane(uv, 0, 0, 1, -this.grabHeight);
        if (q) {
          p.x = q.x;
          p.y = q.y;
        }
        break;
      }
      case 'xLine':
        if (onSurface) p.y = onSurface.y;
        break;
      case 'yLine':
        if (onSurface) p.x = onSurface.x;
        break;
      case 'spin':
        if (atHeight) p.spin = spinDeg(Math.atan2(atHeight.y - at.y, atHeight.x - at.x));
        break;
      case 'tilt': {
        // the vertical plane through the gripper containing the spin direction
        const nx = -Math.sin(s);
        const ny = Math.cos(s);
        const q = cam.uv2xyzPlane(uv, nx, ny, 0, -(nx * at.x + ny * at.y));
        if (q) {
          const along = (q.x - at.x) * Math.cos(s) + (q.y - at.y) * Math.sin(s);
          p.tilt = tiltDeg(Math.atan2(along, q.z - at.z));
        }
        break;
      }
      default:
        break;
    }
    this.pose = p;
    this.onChange({ ...p });
  }

  pointerUp(): void {
    this.dragging = 'none';
    this.cursor = 'default';
    this.pc.focusPart = 'none';
    this.sc.focus = false;
    this.tc.focus = false;
  }
}
