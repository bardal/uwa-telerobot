// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CameraCanvas, fitImage } from '../src/ui/cameraCanvas';
import { Camera } from '../src/geometry/camera';

const P = [-0.3366, -0.0947, -0.0854, 279.3458, 0.006, 0.1172, -0.3273, 115.1475, -0.0000285, -0.000558, -0.000427, 1];

function makeCanvas(w: number, h: number): HTMLCanvasElement {
  const el = document.createElement('canvas');
  Object.defineProperty(el, 'clientWidth', { value: w, configurable: true });
  Object.defineProperty(el, 'clientHeight', { value: h, configurable: true });
  return el;
}

const ctxStub = new Proxy({}, { get: () => () => undefined, set: () => true }) as unknown as CanvasRenderingContext2D;

beforeEach(() => {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctxStub as never);
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
      unobserve() {}
    },
  );
  vi.stubGlobal('devicePixelRatio', 1);
});

const image = { width: 400, height: 300, close() {} } as unknown as ImageBitmap;

describe('fitImage (ImageCanvas.fit)', () => {
  it('1000x600 element, 400x300 image: scale 2, offset (100,0)', () => {
    expect(fitImage(1000, 600, 400, 300)).toEqual({ scale: 2, offset: { x: 100, y: 0 } });
  });
  it('412x309 snaps to scale 1', () => {
    const f = fitImage(412, 309, 400, 300);
    expect(f.scale).toBe(1);
    expect(f.offset).toEqual({ x: 6, y: 5 }); // round(6), round(4.5) half up
  });
  it('200x100 gives scale 1/3', () => {
    const f = fitImage(200, 100, 400, 300);
    expect(f.scale).toBeCloseTo(1 / 3, 12);
    expect(f.offset).toEqual({ x: 33, y: 0 });
  });
  it('a zero-size element resets', () => {
    expect(fitImage(0, 0, 400, 300)).toEqual({ scale: 1, offset: { x: 0, y: 0 } });
  });
});

describe('CameraCanvas', () => {
  it('fits and exposes scale/offset after setImage', () => {
    const cc = new CameraCanvas(makeCanvas(1000, 600));
    cc.setImage(image, 400, 300, P);
    expect(cc.scale).toBe(2);
    expect(cc.offset).toEqual({ x: 100, y: 0 });
  });

  it('camera() is null before an image and display-scaled after', () => {
    const cc = new CameraCanvas(makeCanvas(1000, 600));
    expect(cc.camera()).toBeNull();
    cc.setImage(image, 400, 300, P);
    const plain = new Camera(P).xyz2uv({ x: 250, y: 250, z: 0 });
    const shown = cc.camera()!.xyz2uv({ x: 250, y: 250, z: 0 });
    expect(shown.u).toBeCloseTo(plain.u * 2 + 100, 6);
    expect(shown.v).toBeCloseTo(plain.v * 2, 6);
  });

  it('toImage inverts the display transform', () => {
    const cc = new CameraCanvas(makeCanvas(1000, 600));
    cc.setImage(image, 400, 300, P);
    expect(cc.toImage({ x: 100, y: 0 })).toEqual({ x: 0, y: 0 });
    expect(cc.toImage({ x: 900, y: 600 })).toEqual({ x: 400, y: 300 });
    expect(cc.toImage({ x: 500, y: 300 })).toEqual({ x: 200, y: 150 });
  });

  it('sizes the backing store by devicePixelRatio', () => {
    vi.stubGlobal('devicePixelRatio', 2);
    const el = makeCanvas(300, 200);
    const cc = new CameraCanvas(el);
    cc.setImage({ width: 300, height: 200, close() {} } as unknown as ImageBitmap, 300, 200, P);
    expect(el.width).toBe(600);
    expect(el.height).toBe(400);
    expect(cc.scale).toBe(1);
  });
});

describe('CameraCanvas pointer routing', () => {
  it('routes a drag to the overlay that captures it, and a still click to clickables', () => {
    const el = makeCanvas(400, 300);
    const cc = new CameraCanvas(el);
    cc.setImage(image, 400, 300, P);
    const log: string[] = [];
    cc.add({
      draw() {},
      pointerDown: (uv: { u: number; v: number }) => (log.push(`down ${uv.u},${uv.v}`), uv.u < 50),
      pointerMove: (uv: { u: number; v: number }) => void log.push(`move ${uv.u},${uv.v}`),
      pointerUp: () => void log.push('up'),
    } as never);
    cc.add({ draw() {}, click: (uv: { u: number; v: number }) => (log.push(`click ${uv.u},${uv.v}`), true) } as never);
    const fire = (type: string, x: number, y: number) =>
      el.dispatchEvent(Object.assign(new MouseEvent(type, { clientX: x, clientY: y, button: 0 }), { pointerId: 1 }));
    fire('pointerdown', 10, 20);
    fire('pointermove', 30, 40);
    fire('pointerup', 30, 40);
    fire('pointerdown', 200, 100);
    fire('pointerup', 201, 101);
    expect(log).toEqual(['down 10,20', 'move 30,40', 'up', 'down 200,100', 'click 201,101']);
  });
});
