// Port of model/WorkspaceModel (the points part) and gui/overlaid/OverlaidPoint:
// named points in the workspace, drawn as small crosses with a label. Clicking
// within 5 px of a cross toggles its selection (black unselected, red selected).

import type { Camera, UV } from '../geometry/camera';
import { project, type Clickable } from './types';

export const POINT_SIZE = 5;

export type WorkspacePoint = { name: string; x: number; y: number; z: number };

export class WorkspacePoints implements Clickable {
  visible = true;
  private readonly points = new Map<string, WorkspacePoint>();
  private selectedName: string | null = null;
  onChange: () => void = () => undefined;

  /** Replace everything (the workspace channel sends the whole list). */
  setPoints(list: WorkspacePoint[]): void {
    this.points.clear();
    for (const p of list) this.points.set(p.name, { ...p });
    if (this.selectedName && !this.points.has(this.selectedName)) this.selectedName = null;
    this.onChange();
  }

  add(p: WorkspacePoint): void {
    this.points.set(p.name, { ...p });
    this.onChange();
  }

  delete(name: string): void {
    this.points.delete(name);
    if (this.selectedName === name) this.selectedName = null;
    this.onChange();
  }

  get(name: string): WorkspacePoint | undefined {
    return this.points.get(name);
  }

  names(): string[] {
    return [...this.points.keys()];
  }

  get selected(): WorkspacePoint | null {
    return this.selectedName ? (this.points.get(this.selectedName) ?? null) : null;
  }

  setVisible(v: boolean): void {
    if (this.visible !== v) {
      this.visible = v;
      this.onChange();
    }
  }

  draw(ctx: CanvasRenderingContext2D, cam: Camera): void {
    if (!this.visible) return;
    ctx.lineWidth = 1;
    ctx.textAlign = 'start';
    ctx.textBaseline = 'alphabetic';
    for (const p of this.points.values()) {
      const uv = project(cam, p);
      if (!uv) continue;
      const colour = p.name === this.selectedName ? '#ff0000' : '#000000';
      ctx.strokeStyle = colour;
      ctx.fillStyle = colour;
      ctx.beginPath();
      ctx.moveTo(uv.x - POINT_SIZE, uv.y);
      ctx.lineTo(uv.x + POINT_SIZE, uv.y);
      ctx.moveTo(uv.x, uv.y - POINT_SIZE);
      ctx.lineTo(uv.x, uv.y + POINT_SIZE);
      ctx.stroke();
      ctx.fillText(p.name, uv.x + POINT_SIZE, uv.y);
    }
  }

  /** WorkspaceModel.mouseClicked: the first point under the click toggles and consumes it. */
  click(uv: UV, cam: Camera): boolean {
    if (!this.visible) return false;
    for (const p of this.points.values()) {
      const s = project(cam, p);
      if (s && Math.hypot(s.x - uv.u, s.y - uv.v) < POINT_SIZE) {
        this.selectedName = this.selectedName === p.name ? null : p.name;
        this.onChange();
        return true;
      }
    }
    return false;
  }
}
