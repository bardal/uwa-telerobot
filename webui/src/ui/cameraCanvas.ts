// A camera image scaled to fit its element, with AR overlays and pointer
// interaction. Port of the layout in gui/awt/ImageCanvas (fit, toImage, the
// order of painting) plus the mouse handling CalibratedCanvas gave overlays.

import { Camera, displayMatrix, type P34, type UV } from '../geometry/camera';
import { isClickable, isInteractive, type Interactive, type Overlay3D, type Rect } from '../overlays/types';

export type Fit = { scale: number; offset: { x: number; y: number } };

/**
 * ImageCanvas.fit(): scale to fit keeping the aspect ratio, centred, and snap
 * to 1 when within 3% so a near-exact fit stays sharp. display = image*scale + offset.
 */
export function fitImage(elW: number, elH: number, imgW: number, imgH: number): Fit {
  if (elW <= 0 || elH <= 0 || imgW <= 0 || imgH <= 0) return { scale: 1, offset: { x: 0, y: 0 } };
  let scale = Math.min(elW / imgW, elH / imgH);
  // Java compares `< 0.03`; 412/400 is 1.03 only to within rounding, so allow for it
  if (Math.abs(scale - 1) < 0.03 + 1e-9) scale = 1;
  return {
    scale,
    offset: { x: Math.round((elW - imgW * scale) / 2), y: Math.round((elH - imgH * scale) / 2) },
  };
}

const CLICK_SLOP = 4; // px between press and release that still counts as a click
const BACKGROUND = '#c0c0c0'; // Color.lightGray

export class CameraCanvas {
  private image: ImageBitmap | null = null;
  private imgW = 0;
  private imgH = 0;
  private calibration: P34 | null = null;
  private fit: Fit = { scale: 1, offset: { x: 0, y: 0 } };
  private cam: Camera | null = null;
  private readonly overlays: Overlay3D[] = [];
  private readonly ctx: CanvasRenderingContext2D | null;
  private readonly observer: ResizeObserver | null;
  private captured: Interactive | null = null;
  private downAt: { x: number; y: number } | null = null;
  private frame = 0;

  constructor(private readonly el: HTMLCanvasElement) {
    this.ctx = el.getContext('2d');
    el.style.touchAction = 'none';
    el.addEventListener('pointerdown', this.onDown);
    el.addEventListener('pointermove', this.onMove);
    el.addEventListener('pointerup', this.onUp);
    el.addEventListener('pointercancel', this.onCancel);
    this.observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => this.layout());
    this.observer?.observe(el);
    this.layout();
  }

  get scale(): number {
    return this.fit.scale;
  }

  get offset(): { x: number; y: number } {
    return { ...this.fit.offset };
  }

  setImage(img: ImageBitmap, width: number, height: number, calibration: P34): void {
    const old = this.image;
    this.image = img;
    this.imgW = width;
    this.imgH = height;
    this.calibration = calibration;
    if (old && old !== img) old.close();
    this.layout();
  }

  add(o: Overlay3D): void {
    this.overlays.push(o);
    this.invalidate();
  }

  remove(o: Overlay3D): void {
    const i = this.overlays.indexOf(o);
    if (i >= 0) this.overlays.splice(i, 1);
    this.invalidate();
  }

  /** The camera in display coordinates (image scaled and offset); null until an image is set. */
  camera(): Camera | null {
    return this.cam;
  }

  /** Canvas (display) point to image pixels, as ImageCanvas.toImage. */
  toImage(px: { x: number; y: number }): { x: number; y: number } {
    return {
      x: Math.round((px.x - this.fit.offset.x) / this.fit.scale),
      y: Math.round((px.y - this.fit.offset.y) / this.fit.scale),
    };
  }

  dispose(): void {
    this.observer?.disconnect();
    this.el.removeEventListener('pointerdown', this.onDown);
    this.el.removeEventListener('pointermove', this.onMove);
    this.el.removeEventListener('pointerup', this.onUp);
    this.el.removeEventListener('pointercancel', this.onCancel);
    if (this.frame) cancelAnimationFrame(this.frame);
    this.image?.close();
    this.image = null;
  }

  /** Redraw on the next animation frame (coalesces bursts of changes). */
  invalidate(): void {
    if (typeof requestAnimationFrame === 'undefined') {
      this.draw();
      return;
    }
    if (this.frame) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = 0;
      this.draw();
    });
  }

  private layout(): void {
    const w = this.el.clientWidth;
    const h = this.el.clientHeight;
    const dpr = globalThis.devicePixelRatio || 1;
    const bw = Math.max(1, Math.round(w * dpr));
    const bh = Math.max(1, Math.round(h * dpr));
    if (this.el.width !== bw) this.el.width = bw;
    if (this.el.height !== bh) this.el.height = bh;
    this.fit = fitImage(w, h, this.imgW, this.imgH);
    this.cam = this.calibration
      ? new Camera(displayMatrix(this.calibration, this.fit.scale, this.fit.offset.x, this.fit.offset.y))
      : null;
    this.invalidate();
  }

  private imageRect(): Rect {
    return {
      x: this.fit.offset.x,
      y: this.fit.offset.y,
      width: Math.round(this.imgW * this.fit.scale),
      height: Math.round(this.imgH * this.fit.scale),
    };
  }

  private draw(): void {
    const ctx = this.ctx;
    if (!ctx) return;
    const dpr = globalThis.devicePixelRatio || 1;
    const w = this.el.clientWidth;
    const h = this.el.clientHeight;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.fillStyle = BACKGROUND;
    ctx.fillRect(0, 0, w, h);
    const r = this.imageRect();
    ctx.save();
    // overlays drawn after the image stay on the image
    ctx.beginPath();
    ctx.rect(r.x, r.y, r.width, r.height);
    ctx.clip();
    if (this.image) {
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(this.image, r.x, r.y, r.width, r.height);
    } else {
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(r.x, r.y, r.width - 1, r.height - 1);
    }
    ctx.strokeStyle = '#000000';
    ctx.lineWidth = 1;
    ctx.strokeRect(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1);
    const cam = this.cam;
    if (cam) {
      for (const o of this.overlays) {
        o.setClip?.(r);
        ctx.save();
        o.draw(ctx, cam);
        ctx.restore();
      }
    }
    ctx.restore();
  }

  private toUV(e: PointerEvent): UV {
    const b = this.el.getBoundingClientRect();
    return { u: Math.round(e.clientX - b.left), v: Math.round(e.clientY - b.top) };
  }

  private readonly onDown = (e: PointerEvent): void => {
    const cam = this.cam;
    if (!cam || this.captured) return;
    const uv = this.toUV(e);
    this.downAt = e.button === 0 ? { x: uv.u, y: uv.v } : null;
    for (const o of this.overlays) {
      if (isInteractive(o) && o.pointerDown(uv, cam)) {
        this.captured = o;
        this.downAt = null;
        this.el.setPointerCapture?.(e.pointerId);
        e.preventDefault();
        this.el.style.cursor = (o as { cursor?: string }).cursor ?? 'pointer';
        this.invalidate();
        return;
      }
    }
  };

  private readonly onMove = (e: PointerEvent): void => {
    const cam = this.cam;
    if (!cam || !this.captured) return;
    this.captured.pointerMove(this.toUV(e), cam);
    this.invalidate();
  };

  private readonly onUp = (e: PointerEvent): void => {
    const cam = this.cam;
    if (this.captured) {
      this.captured.pointerUp();
      this.captured = null;
      this.el.style.cursor = '';
      this.el.releasePointerCapture?.(e.pointerId);
      this.invalidate();
      return;
    }
    const down = this.downAt;
    this.downAt = null;
    if (!cam || !down) return;
    const uv = this.toUV(e);
    if (Math.hypot(uv.u - down.x, uv.v - down.y) > CLICK_SLOP) return;
    for (const o of this.overlays) {
      if (isClickable(o) && o.click(uv, cam)) {
        e.preventDefault();
        break;
      }
    }
    this.invalidate();
  };

  private readonly onCancel = (): void => {
    this.captured?.pointerUp();
    this.captured = null;
    this.downAt = null;
    this.el.style.cursor = '';
    this.invalidate();
  };
}
