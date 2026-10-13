// Port of overlaid/TiltCursor: a line from the gripper position along the tool
// axis, vertical at zero tilt and leaning towards the spin direction.

import type { Camera } from '../geometry/camera';
import { projectSeg, RED, spinRad, tiltRad, YELLOW, type Pose, type Pt, type Seg } from './types';

export const TILT_LENGTH = 50; // mm

export class TiltNeedle {
  visible = true;
  focus = false;
  colourFocus = RED;
  colourNoFocus = YELLOW;

  project(cam: Camera, pose: Pose): Seg | null {
    const s = spinRad(pose.spin);
    const t = tiltRad(pose.tilt);
    const h = TILT_LENGTH * Math.sin(t);
    return projectSeg(
      cam,
      { x: pose.x, y: pose.y, z: pose.z },
      { x: pose.x + h * Math.cos(s), y: pose.y + h * Math.sin(s), z: pose.z + TILT_LENGTH * Math.cos(t) },
    );
  }

  draw(ctx: CanvasRenderingContext2D, cam: Camera, pose: Pose): void {
    if (!this.visible) return;
    const tilter = this.project(cam, pose);
    if (!tilter) return;
    tilter.draw(ctx, this.focus ? this.colourFocus : this.colourNoFocus);
    ctx.beginPath();
    ctx.arc(tilter.end.x, tilter.end.y, 3, 0, Math.PI * 2);
    ctx.stroke();
  }

  contains(m: Pt, cam: Camera, pose: Pose): boolean {
    if (!this.visible) return false;
    const tilter = this.project(cam, pose);
    return !!tilter && tilter.contains(m) && tilter.proportion(m) > 0.3;
  }
}
