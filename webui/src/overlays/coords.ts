// Port of TA200001.StickCursorCoords and Pose3d.toDisplayString: the red
// "x=.. y=.. z=.. s=..° t=..°" text at (10, 20) on each camera image.

import type { Overlay3D, Pose } from './types';

/** Math.round in Java and JS both round half up; "-0" prints as "0" in both. */
export function formatPose(p: Pose): string {
  return (
    `x=${Math.round(p.x)} y=${Math.round(p.y)} z=${Math.round(p.z)} ` +
    `s=${Math.round(p.spin)}° t=${Math.round(p.tilt)}°`
  );
}

export class StickCursorCoords implements Overlay3D {
  visible = true;

  /** fontSize is the applet's "fontsize" parameter (default 10); the text is bold at fontSize + 1. */
  constructor(
    private readonly getPose: () => Pose,
    private readonly fontSize = 10,
  ) {}

  text(): string {
    return formatPose(this.getPose());
  }

  draw(ctx: CanvasRenderingContext2D): void {
    if (!this.visible) return;
    ctx.fillStyle = '#ff0000';
    ctx.font = `bold ${this.fontSize + 1}px Helvetica, Arial, sans-serif`;
    ctx.textAlign = 'start';
    ctx.textBaseline = 'alphabetic';
    ctx.fillText(this.text(), 10, 20);
  }
}
