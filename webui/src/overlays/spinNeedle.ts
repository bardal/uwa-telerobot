// Port of overlaid/SpinCursor: a horizontal needle from the gripper position
// pointing along the spin angle.

import type { Camera } from '../geometry/camera';
import { projectSeg, RED, spinRad, YELLOW, type Pose, type Pt, type Seg } from './types';

export const SPIN_LENGTH = 50; // mm

export class SpinNeedle {
  visible = true;
  focus = false;
  colourFocus = RED;
  colourNoFocus = YELLOW;

  project(cam: Camera, pose: Pose): Seg | null {
    const s = spinRad(pose.spin);
    return projectSeg(
      cam,
      { x: pose.x, y: pose.y, z: pose.z },
      { x: pose.x + SPIN_LENGTH * Math.cos(s), y: pose.y + SPIN_LENGTH * Math.sin(s), z: pose.z },
    );
  }

  draw(ctx: CanvasRenderingContext2D, cam: Camera, pose: Pose): void {
    if (!this.visible) return;
    const needle = this.project(cam, pose);
    if (!needle) return;
    const colour = this.focus ? this.colourFocus : this.colourNoFocus;
    needle.draw(ctx, colour);
    ctx.fillStyle = colour;
    ctx.fillRect(needle.end.x - 2, needle.end.y - 2, 5, 5);
  }

  /** Grabbing is by the outer part of the needle, so the peak marker stays reachable. */
  contains(m: Pt, cam: Camera, pose: Pose): boolean {
    if (!this.visible) return false;
    const needle = this.project(cam, pose);
    return !!needle && needle.contains(m) && needle.proportion(m) > 0.3;
  }
}
