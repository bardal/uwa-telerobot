// Port of overlaid/Axes: small x, y, z arrows drawn at the projected origin
// (or in the nearest inset corner when the origin is off the image).

import type { Camera, Vec3 } from '../geometry/camera';
import { project, type Overlay3D, type Pt, type Rect } from './types';

const INSET = 25;
const LENGTH = 25;
const ARROW_LENGTH = 7;
const AXES: Vec3[] = [
  { x: 500, y: 0, z: 0 },
  { x: 0, y: 500, z: 0 },
  { x: 0, y: 0, z: 500 },
];
const LABELS = ['x', 'y', 'z'];
const ORIGIN: Vec3 = { x: 0, y: 0, z: 0 };

const norm = (v: Pt): Pt => {
  const s = Math.hypot(v.x, v.y);
  return { x: v.x / s, y: v.y / s };
};
const roundPt = (x: number, y: number): Pt => ({ x: Math.round(x), y: Math.round(y) });

function findBestCorner(r: Rect, p: Pt): Pt {
  const corners: Pt[] = [
    { x: r.x, y: r.y },
    { x: r.x + r.width, y: r.y },
    { x: r.x + r.width, y: r.y + r.height },
    { x: r.x, y: r.y + r.height },
  ];
  let best = corners[0]!;
  let min = Infinity;
  for (const c of corners) {
    const d = Math.hypot(p.x - c.x, p.y - c.y);
    if (d < min) {
      best = c;
      min = d;
    }
  }
  return best;
}

export class Axes implements Overlay3D {
  visible = true;
  private clip: Rect | null = null;

  setClip(r: Rect): void {
    this.clip = r;
  }

  draw(ctx: CanvasRenderingContext2D, cam: Camera): void {
    if (!this.visible) return;
    const o = project(cam, ORIGIN);
    if (!o) return;
    let start = o;
    if (this.clip) {
      const r = this.clip;
      const inside = o.x >= r.x && o.y >= r.y && o.x < r.x + r.width && o.y < r.y + r.height;
      if (!inside) {
        start = findBestCorner(
          { x: r.x + INSET, y: r.y + INSET, width: r.width - 2 * INSET, height: r.height - 2 * INSET },
          o,
        );
      }
    }
    ctx.strokeStyle = '#000000';
    ctx.fillStyle = '#000000';
    ctx.lineWidth = 1;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'alphabetic';
    AXES.forEach((axis, i) => {
      const e = project(cam, axis);
      if (!e) return;
      const dir = norm({ x: e.x - o.x, y: e.y - o.y });
      if (!Number.isFinite(dir.x) || !Number.isFinite(dir.y)) return;
      const end = roundPt(start.x + dir.x * LENGTH, start.y + dir.y * LENGTH);
      ctx.beginPath();
      ctx.moveTo(start.x, start.y);
      ctx.lineTo(end.x, end.y);
      ctx.stroke();
      drawArrow(ctx, end, dir);
      const text = roundPt(
        start.x + dir.x * (LENGTH + Math.trunc(ARROW_LENGTH * 1.5)),
        start.y + dir.y * (LENGTH + Math.trunc(ARROW_LENGTH * 1.5)),
      );
      ctx.fillText(LABELS[i]!, text.x, text.y);
    });
    ctx.textAlign = 'start';
  }
}

function drawArrow(ctx: CanvasRenderingContext2D, base: Pt, unit: Pt): void {
  const h = Math.trunc(ARROW_LENGTH / 2);
  const tip = { x: unit.x * ARROW_LENGTH, y: unit.y * ARROW_LENGTH };
  const left = { x: -unit.y * h, y: unit.x * h }; // rot90, anticlockwise
  const right = { x: unit.y * h, y: -unit.x * h }; // rotMinus90, clockwise
  ctx.beginPath();
  ctx.moveTo(Math.round(base.x + tip.x), Math.round(base.y + tip.y));
  ctx.lineTo(Math.round(base.x + left.x), Math.round(base.y + left.y));
  ctx.lineTo(Math.round(base.x + right.x), Math.round(base.y + right.y));
  ctx.closePath();
  ctx.fill();
}
