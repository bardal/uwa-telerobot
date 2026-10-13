// Port of overlaid/TapeMeasure: measure the distance between two points, each
// triangulated with PointMeasure from two camera images.

import type { Camera, UV, Vec3 } from '../geometry/camera';
import { PointMeasure } from './pointMeasure';
import { drawCrossHairs, project, type Clickable, type Rect } from './types';

export class TapeMeasure implements Clickable {
  private _enabled = false;
  point1: Vec3 | null = null;
  point2: Vec3 | null = null;
  private readonly pm = new PointMeasure();
  onMessage: (s: string) => void = () => undefined;
  onChange: () => void = () => undefined;

  constructor() {
    this.pm.onMessage = (s) => this.onMessage(s);
    this.pm.onChange = () => this.onChange();
    this.pm.onMeasured = (p) => this.pointMeasured(p);
  }

  get enabled(): boolean {
    return this._enabled;
  }
  set enabled(v: boolean) {
    if (this._enabled !== v) {
      this._enabled = v;
      this.point1 = null;
      this.point2 = null;
      this.pm.enabled = v;
    }
  }

  setClip(r: Rect): void {
    this.pm.setClip(r);
  }

  start(): void {
    this.clear();
    this.onMessage('Click on the start of the line');
  }

  clear(): void {
    this.point1 = null;
    this.point2 = null;
    this.pm.clear();
    this.pm.enabled = true;
    this.onChange();
  }

  /** Length of the measured line in mm, once both ends are set. */
  length(): number | null {
    const a = this.point1;
    const b = this.point2;
    return a && b ? Math.hypot(b.x - a.x, b.y - a.y, b.z - a.z) : null;
  }

  private pointMeasured(p: Vec3): void {
    if (!this.point1) {
      this.point1 = p;
      this.pm.clear();
      this.onMessage('Now click on the end of the line');
    } else if (!this.point2) {
      this.point2 = p;
      this.onMessage(`Measured line length ${Math.round(this.length()!)}mm`);
      this.pm.clear();
      this.pm.enabled = false;
    }
  }

  click(uv: UV, cam: Camera): boolean {
    return this.pm.click(uv, cam);
  }

  draw(ctx: CanvasRenderingContext2D, cam: Camera): void {
    if (!this._enabled) return;
    this.pm.draw(ctx, cam);
    ctx.lineWidth = 1;
    const p1 = this.point1 && project(cam, this.point1);
    if (p1) drawCrossHairs(ctx, p1);
    const p2 = this.point2 && project(cam, this.point2);
    if (p2) drawCrossHairs(ctx, p2);
    if (p1 && p2) {
      // Java drew with whatever colour was current (CrossHairs restores it); the
      // canvas paints the image border last in black, so use black.
      ctx.strokeStyle = '#000000';
      ctx.beginPath();
      ctx.moveTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
      ctx.stroke();
    }
  }
}
