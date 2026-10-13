// Shared overlay types and helpers. Ports of overlaid/Overlay3D, Line and the
// pose conversions in maths/Orientation3d.

import type { Camera, UV, Vec3 } from '../geometry/camera';

/** Position in mm; spin and tilt in degrees as in GBH commands (Pose3d.spinDeg / tiltDeg). */
export type Pose = { x: number; y: number; z: number; spin: number; tilt: number };

export type Pt = { x: number; y: number };
export type Rect = { x: number; y: number; width: number; height: number };

export interface Overlay3D {
  /** Draw in display coordinates; cam is the display-scaled camera. */
  draw(ctx: CanvasRenderingContext2D, cam: Camera): void;
  /** Called by the canvas before draw with the visible image rectangle (Java's clip bounds). */
  setClip?(r: Rect): void;
}

export interface Interactive extends Overlay3D {
  pointerDown(uv: UV, cam: Camera): boolean; // true = captured
  pointerMove(uv: UV, cam: Camera): void;
  pointerUp(): void;
}

/** A press-and-release without drag (Java's mouseClicked). True = consumed. */
export interface Clickable extends Overlay3D {
  click(uv: UV, cam: Camera): boolean;
}

export function isInteractive(o: Overlay3D): o is Interactive {
  return typeof (o as Interactive).pointerDown === 'function';
}

export function isClickable(o: Overlay3D): o is Clickable {
  return typeof (o as Clickable).click === 'function';
}

/** Orientation3d.spinDeg(s): internal radians = (s - spinOffset)/180*PI with spinOffset = -90. */
export const spinRad = (deg: number): number => ((deg + 90) / 180) * Math.PI;
export const spinDeg = (rad: number): number => (rad * 180) / Math.PI - 90;
/** Orientation3d.tiltDeg(t): internal radians = -t/180*PI. */
export const tiltRad = (deg: number): number => (-deg / 180) * Math.PI;
export const tiltDeg = (rad: number): number => (-rad * 180) / Math.PI;

/** Camera.xyz2uv as a whole-pixel Point; null if the point is behind the camera. */
export function project(cam: Camera, w: Vec3): Pt | null {
  if (!cam.inFront(w)) return null;
  return cam.toPoint(cam.xyz2uv(w));
}

export const TOLERANCE = 4; // Line.TOLERANCE

/** A 2D segment in display coordinates, with hit testing (port of overlaid/Line). */
export class Seg {
  constructor(
    readonly start: Pt,
    readonly end: Pt,
  ) {}

  draw(ctx: CanvasRenderingContext2D, colour: string): void {
    ctx.strokeStyle = colour;
    ctx.beginPath();
    ctx.moveTo(this.start.x, this.start.y);
    ctx.lineTo(this.end.x, this.end.y);
    ctx.stroke();
  }

  distance(p: Pt): number {
    const dx = this.end.x - this.start.x;
    const dy = this.end.y - this.start.y;
    const len2 = dx * dx + dy * dy;
    let t = len2 === 0 ? 0 : ((p.x - this.start.x) * dx + (p.y - this.start.y) * dy) / len2;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(this.start.x + t * dx - p.x, this.start.y + t * dy - p.y);
  }

  /** Where along the segment the point nearest p lies: 0 at start, 1 at end. */
  proportion(p: Pt): number {
    const dx = this.end.x - this.start.x;
    const dy = this.end.y - this.start.y;
    const len2 = dx * dx + dy * dy;
    if (len2 === 0) return 0;
    return Math.max(0, Math.min(1, ((p.x - this.start.x) * dx + (p.y - this.start.y) * dy) / len2));
  }

  contains(p: Pt): boolean {
    return this.distance(p) <= TOLERANCE;
  }
}

/** A segment between two world points, or null if either end is behind the camera. */
export function projectSeg(cam: Camera, a: Vec3, b: Vec3): Seg | null {
  const pa = project(cam, a);
  const pb = project(cam, b);
  return pa && pb ? new Seg(pa, pb) : null;
}


/** Port of CrossHairs.overlay: a red cross of half-size 5. */
export function drawCrossHairs(ctx: CanvasRenderingContext2D, p: Pt, size = 5): void {
  ctx.strokeStyle = '#ff0000';
  ctx.beginPath();
  ctx.moveTo(p.x - size, p.y);
  ctx.lineTo(p.x + size, p.y);
  ctx.moveTo(p.x, p.y - size);
  ctx.lineTo(p.x, p.y + size);
  ctx.stroke();
}

export const YELLOW = '#ffff00'; // Color.yellow, OverlaidComponent colorNoFocus
export const RED = '#ff0000'; // Color.red, colorFocus
