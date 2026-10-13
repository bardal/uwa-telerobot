// Port of devices/camera/CameraManagerPanel, CameraPanel and CameraControlsPanel:
// a button per camera above a card stack of camera panels; each panel is the
// image canvas (Task 10's CameraCanvas) over its settings row.

import { displayMatrix, type P34 } from '../geometry/camera';
import { StickCursor } from '../overlays/stickCursor';
import type { Overlay3D, Pose } from '../overlays/types';
import type { In, Out } from '../net/protocol';
import { CameraCanvas } from './cameraCanvas';
import { button, h } from './dom';

type Settings = Extract<Out, { t: 'cameraSettings' }>;
type SettingsIn = Extract<In, { t: 'cameraSettings' }>;

export interface CamerasPanelDeps {
  /** A camera button was pressed (the card is already shown). */
  onSelect: (camera: string) => void;
  onSettings: (s: SettingsIn) => void;
  onNewImage: (camera: string) => void;
  /** A drag moved this camera's stick cursor. */
  onCursor: (pose: Pose, from: StickCursor) => void;
  /** Right button pressed on a canvas: show the popup at viewport (x, y). */
  onPopup: (x: number, y: number) => void;
  /** Overlays every canvas shares, after its own stick cursor (TA200001.created order). */
  shared: () => Overlay3D[];
  println: (text: string) => void;
}

/** MathExtras.limit. */
const limit = (v: number, lo: number, hi: number): number => Math.min(hi, Math.max(lo, v));

/** IntegerTextField / FloatTextField: a number field that keeps its last good value. */
class NumberField {
  readonly el: HTMLInputElement;
  private good: number;

  constructor(value: number, cols: number, label: string, private readonly integer: boolean) {
    this.good = value;
    this.el = h('input', { type: 'text', class: 'awt-text num-field', size: cols, 'aria-label': label, inputmode: integer ? 'numeric' : 'decimal' });
    this.setValue(value);
  }

  getValue(): number {
    const v = this.integer ? Number.parseInt(this.el.value, 10) : Number.parseFloat(this.el.value);
    if (Number.isFinite(v)) this.good = v;
    else this.setValue(this.good);
    return this.good;
  }

  setValue(v: number): void {
    this.good = v;
    this.el.value = this.integer ? String(Math.round(v)) : Number.isInteger(v) ? v.toFixed(1) : String(v);
  }
}

class CameraControls {
  readonly el: HTMLElement;
  readonly widthField = new NumberField(400, 5, 'width', true);
  readonly heightField = new NumberField(400, 5, 'height', true);
  readonly qualityField = new NumberField(100, 4, 'quality', true);
  readonly zoomField = new NumberField(0, 4, 'zoom', false);
  readonly fixedCB: HTMLInputElement;
  readonly onBox: HTMLInputElement;
  readonly shutter: HTMLButtonElement;
  readonly changeSettings: HTMLButtonElement;

  constructor(
    readonly camera: string,
    private readonly deps: CamerasPanelDeps,
  ) {
    this.fixedCB = h('input', { type: 'checkbox', class: 'awt-check', 'aria-label': 'fixed' });
    this.onBox = h('input', { type: 'checkbox', class: 'awt-check', 'aria-label': 'on' });
    this.onBox.checked = true;
    this.shutter = button('New Image', () => this.deps.onNewImage(this.camera));
    this.changeSettings = button('Change Settings', () => this.deps.onSettings(this.settings(this.onBox.checked)));
    this.onBox.addEventListener('change', () => {
      this.deps.onSettings(this.settings(this.onBox.checked));
      this.setEnabled(this.onBox.checked);
    });
    const cell = (label: string, field: HTMLElement) => h('div', { class: 'cam-cell' }, h('div', { class: 'awt-label', text: label }), field);
    this.el = h(
      'div',
      { class: 'camera-controls' },
      cell('on', this.onBox),
      h(
        'div',
        { class: 'cam-settings' },
        cell('width', this.widthField.el),
        cell('height', this.heightField.el),
        cell('zoom', this.zoomField.el),
        cell('quality', this.qualityField.el),
        cell('fixed', this.fixedCB),
        h('div', { class: 'cam-buttons' }, this.shutter, this.changeSettings),
      ),
    );
  }

  /** CameraControlsPanel.actionPerformed(changeSettings): zoom limited to 0..1, quality to 0..50. */
  settings(on: boolean): SettingsIn {
    return {
      t: 'cameraSettings',
      camera: this.camera,
      on,
      width: this.widthField.getValue(),
      height: this.heightField.getValue(),
      zoom: limit(this.zoomField.getValue(), 0, 1),
      quality: limit(this.qualityField.getValue(), 0, 50),
      fixed: this.fixedCB.checked,
    };
  }

  setEnabled(b: boolean): void {
    for (const c of [this.widthField.el, this.heightField.el, this.qualityField.el, this.zoomField.el, this.changeSettings, this.shutter, this.fixedCB]) {
      c.disabled = !b;
    }
  }

  /** updateGUI(settings), plus the on box (the gateway is the authority on it). */
  update(s: Settings): void {
    this.widthField.setValue(s.width);
    this.heightField.setValue(s.height);
    this.zoomField.setValue(s.zoom);
    this.qualityField.setValue(s.quality);
    this.fixedCB.checked = s.fixed;
    this.onBox.checked = s.on;
    this.setEnabled(s.on);
  }
}

export interface CameraView {
  name: string;
  button: HTMLButtonElement;
  card: HTMLElement;
  canvasEl: HTMLCanvasElement;
  canvas: CameraCanvas;
  stick: StickCursor;
  controls: CameraControls;
  calibration: P34 | null;
  seq: number;
}

export class CamerasPanel {
  readonly el: HTMLElement;
  private readonly buttons: HTMLElement;
  private readonly cards: HTMLElement;
  private readonly views = new Map<string, CameraView>();
  private selectedName: string | null = null;

  constructor(private readonly deps: CamerasPanelDeps) {
    this.buttons = h('div', { class: 'camera-buttons' });
    this.cards = h('div', { class: 'camera-cards' });
    this.el = h('div', { class: 'camera-manager' }, this.buttons, this.cards);
  }

  get selected(): string | null {
    return this.selectedName;
  }

  names(): string[] {
    return [...this.views.keys()];
  }

  view(name: string): CameraView | undefined {
    return this.views.get(name);
  }

  allViews(): CameraView[] {
    return [...this.views.values()];
  }

  /** newCamera for each name not yet known; the first camera's card is shown (CardLayout). */
  setCameras(names: string[], cursorPose: Pose, cursorVisible: boolean): void {
    for (const name of names) {
      if (this.views.has(name)) continue;
      const b = button(name, () => this.press(name), 'camera-button');
      this.buttons.appendChild(b);
      const canvasEl = h('canvas', { class: 'camera-canvas', 'aria-label': `${name} camera` });
      const wrap = h('div', { class: 'canvas-wrap' }, canvasEl);
      const controls = new CameraControls(name, this.deps);
      const card = h('div', { class: 'camera-card' }, wrap, controls.el);
      card.hidden = true;
      this.cards.appendChild(card);
      const canvas = new CameraCanvas(canvasEl);
      const stick = new StickCursor();
      stick.setPose(cursorPose);
      stick.visible = cursorVisible;
      stick.onChange = (p) => this.deps.onCursor(p, stick);
      canvas.add(stick);
      for (const o of this.deps.shared()) canvas.add(o);
      // the right button (or Ctrl-click, the Mac popup trigger) opens the popup instead of
      // reaching the overlays (capture phase, on the wrapper)
      wrap.addEventListener(
        'pointerdown',
        (e) => {
          if (e.button !== 2 && !(e.button === 0 && e.ctrlKey)) return;
          e.stopPropagation();
          e.preventDefault();
          this.deps.onPopup(e.clientX, e.clientY);
        },
        true,
      );
      wrap.addEventListener('contextmenu', (e) => e.preventDefault());
      this.views.set(name, { name, button: b, card, canvasEl, canvas, stick, controls, calibration: null, seq: -1 });
      if (this.selectedName === null) this.show(name);
    }
  }

  /** CardLayout.show. */
  show(name: string): void {
    if (!this.views.has(name)) return;
    this.selectedName = name;
    for (const v of this.views.values()) {
      v.card.hidden = v.name !== name;
      v.button.classList.toggle('selected', v.name === name);
    }
    this.views.get(name)?.canvas.invalidate();
  }

  private press(name: string): void {
    this.show(name);
    this.deps.onSelect(name);
  }

  setImage(name: string, seq: number, img: ImageBitmap, width: number, height: number, calibration: P34): void {
    const v = this.views.get(name);
    if (!v || seq < v.seq) {
      img.close();
      return;
    }
    v.seq = seq;
    v.calibration = calibration.slice();
    v.canvas.setImage(img, width, height, calibration);
  }

  /** Lets a later frame numbering (a new gateway connection) start again from 0. */
  resetSequence(): void {
    for (const v of this.views.values()) v.seq = -1;
  }

  /** CameraClient prints "New settings for camera X" and the panel fills its row. */
  newSettings(s: Settings): void {
    const v = this.views.get(s.camera);
    if (!v) return;
    this.deps.println(`New settings for camera ${s.camera}`);
    v.controls.update(s);
  }

  /** The display-scaled P34 the canvas projects with, or null before an image. */
  cameraMatrix(name: string): P34 | null {
    const v = this.views.get(name);
    if (!v || !v.calibration || !v.canvas.camera()) return null;
    const o = v.canvas.offset;
    return displayMatrix(v.calibration, v.canvas.scale, o.x, o.y);
  }

  /**
   * Redraws every camera, hidden ones included: as in the applet, each camera keeps showing
   * its last frame with the current overlays (measuring needs two views), and only the
   * selected camera receives new frames.
   */
  invalidate(): void {
    for (const v of this.views.values()) v.canvas.invalidate();
  }

  dispose(): void {
    for (const v of this.views.values()) v.canvas.dispose();
  }
}
