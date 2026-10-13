// Port of overlaid/PlumblineCursor: the positional part of the stick cursor. A
// "peak" marker at the gripper, a plumb line dropped to a "base" marker on the
// work surface, and two base lines through the base parallel to x and y.

import type { Camera } from '../geometry/camera';
import { project, projectSeg, RED, Seg, YELLOW, type Pose, type Pt } from './types';

export type PlumblinePart = 'none' | 'peak' | 'base' | 'height' | 'xLine' | 'yLine';

export const BASELINE_HALF_LENGTH = 60; // mm
export const MARKER_RADIUS = 5;

type Projected = {
  peak: Pt | null;
  base: Pt | null;
  height: Seg | null;
  xLine: Seg | null;
  yLine: Seg | null;
};

export class Plumbline {
  baselinesVisible = true;
  focusPart: PlumblinePart = 'none';
  visible = true;
  colourFocus = RED;
  colourNoFocus = YELLOW;

  project(cam: Camera, pose: Pose): Projected {
    const { x, y, z } = pose;
    const l = BASELINE_HALF_LENGTH;
    const peak = project(cam, { x, y, z });
    const base = project(cam, { x, y, z: 0 });
    return {
      peak,
      base,
      height: peak && base ? new Seg(base, peak) : null,
      xLine: projectSeg(cam, { x: x - l, y, z: 0 }, { x: x + l, y, z: 0 }),
      yLine: projectSeg(cam, { x, y: y - l, z: 0 }, { x, y: y + l, z: 0 }),
    };
  }

  private colourFor(p: PlumblinePart): string {
    return this.focusPart === p ? this.colourFocus : this.colourNoFocus;
  }

  draw(ctx: CanvasRenderingContext2D, cam: Camera, pose: Pose): void {
    if (!this.visible) return;
    const g = this.project(cam, pose);
    if (this.baselinesVisible) {
      g.xLine?.draw(ctx, this.colourFor('xLine'));
      g.yLine?.draw(ctx, this.colourFor('yLine'));
    }
    g.height?.draw(ctx, this.colourFor('height'));
    if (g.base) marker(ctx, g.base, this.colourFor('base'));
    if (g.peak) marker(ctx, g.peak, this.colourFor('peak'));
  }

  /** Which part, if any, display point m is on. Markers win over lines. */
  partAt(m: Pt, cam: Camera, pose: Pose): PlumblinePart {
    if (!this.visible) return 'none';
    const g = this.project(cam, pose);
    if (g.peak && Math.hypot(m.x - g.peak.x, m.y - g.peak.y) <= MARKER_RADIUS + 2) return 'peak';
    if (g.base && Math.hypot(m.x - g.base.x, m.y - g.base.y) <= MARKER_RADIUS + 2) return 'base';
    if (this.baselinesVisible && g.xLine?.contains(m)) return 'xLine';
    if (this.baselinesVisible && g.yLine?.contains(m)) return 'yLine';
    if (g.height?.contains(m)) return 'height';
    return 'none';
  }

  /** Fraction of the way up the plumb line (0 = base, 1 = peak) nearest m. */
  heightFraction(m: Pt, cam: Camera, pose: Pose): number {
    return this.project(cam, pose).height?.proportion(m) ?? 0;
  }
}

function marker(ctx: CanvasRenderingContext2D, c: Pt, colour: string): void {
  ctx.strokeStyle = colour;
  ctx.beginPath();
  ctx.arc(c.x, c.y, MARKER_RADIUS, 0, Math.PI * 2);
  ctx.stroke();
}
