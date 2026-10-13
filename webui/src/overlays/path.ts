// Port of overlaid/Path: the planned path as blue segments between 3D nodes.

import type { Camera, Vec3 } from '../geometry/camera';
import { projectSeg, type Overlay3D } from './types';

export class Path implements Overlay3D {
  nodes: Vec3[] = [];
  onChange: () => void = () => undefined;

  draw(ctx: CanvasRenderingContext2D, cam: Camera): void {
    if (this.nodes.length < 2) return;
    ctx.lineWidth = 1;
    let last = this.nodes[0]!;
    for (const node of this.nodes) {
      projectSeg(cam, last, node)?.draw(ctx, '#0000ff');
      last = node;
    }
  }

  addPoint(p: Vec3): void {
    this.nodes.push({ x: p.x, y: p.y, z: p.z });
    this.onChange();
  }

  /** Clear all but the last node, which is where the robot is now. */
  clear(): void {
    const last = this.nodes[this.nodes.length - 1];
    this.nodes = last ? [last] : [];
    this.onChange();
  }
}
