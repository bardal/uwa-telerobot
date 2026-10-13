import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { Camera, displayMatrix, type UV, type Vec3 } from '../src/geometry/camera';
import { StickCursor, type Pose } from '../src/overlays/stickCursor';
import { formatPose, StickCursorCoords } from '../src/overlays/coords';
import { PointMeasure } from '../src/overlays/pointMeasure';
import { TapeMeasure } from '../src/overlays/tapeMeasure';
import { Path } from '../src/overlays/path';
import { spinRad, tiltRad } from '../src/overlays/types';

interface Fixture {
  cameras: { name: string; P: number[][] }[];
}
const fixture = JSON.parse(
  readFileSync(new URL('./fixtures/camera.json', import.meta.url), 'utf8'),
) as Fixture;
const colourP = fixture.cameras.find((c) => c.name === 'Colour')!.P.flat();
const ycamP = fixture.cameras.find((c) => c.name === 'YCam')!.P.flat();

/** Where a mouse would be to point at w, rounded to whole pixels like Java's Point. */
function at(cam: Camera, x: number, y: number, z: number): UV {
  const p = cam.toPoint(cam.xyz2uv({ x, y, z }));
  return { u: p.x, v: p.y };
}

const views: [string, Camera][] = [
  ['image scale', new Camera(colourP)],
  ['display scale 2.0 offset (100,0)', new Camera(displayMatrix(colourP, 2, 100, 0))],
];

describe.each(views)('stick cursor at %s', (_name, cam) => {
  const start = (p: Partial<Pose>): { sc: StickCursor; changes: Pose[] } => {
    const sc = new StickCursor();
    const changes: Pose[] = [];
    sc.onChange = (q) => changes.push({ ...q });
    sc.setPose({ x: 250, y: 250, z: 100, spin: 0, tilt: 0, ...p });
    return { sc, changes };
  };

  it('dragging the base moves x/y on the z=0 plane and leaves z', () => {
    const { sc, changes } = start({});
    expect(sc.pointerDown(at(cam, 250, 250, 0), cam)).toBe(true);
    sc.pointerMove(at(cam, 150, 350, 0), cam);
    sc.pointerUp();
    expect(Math.abs(sc.pose.x - 150)).toBeLessThan(3);
    expect(Math.abs(sc.pose.y - 350)).toBeLessThan(3);
    expect(sc.pose.z).toBe(100);
    expect(changes.length).toBeGreaterThan(0);
  });

  it('dragging the peak changes z only', () => {
    const { sc } = start({ x: 150, y: 350, z: 100 });
    expect(sc.pointerDown(at(cam, 150, 350, 100), cam)).toBe(true);
    sc.pointerMove(at(cam, 150, 350, 40), cam);
    sc.pointerUp();
    expect(Math.abs(sc.pose.z - 40)).toBeLessThan(3);
    expect(Math.abs(sc.pose.x - 150)).toBeLessThan(0.5);
    expect(Math.abs(sc.pose.y - 350)).toBeLessThan(0.5);
  });

  it('dragging the plumbline moves x/y at the height grabbed', () => {
    const { sc } = start({ z: 300 });
    // halfway up the plumbline on screen: the grab height is then z/2 = 150
    const b = at(cam, 250, 250, 0);
    const t = at(cam, 250, 250, 300);
    expect(sc.pointerDown({ u: (b.u + t.u) / 2, v: (b.v + t.v) / 2 }, cam)).toBe(true);
    sc.pointerMove(at(cam, 200, 300, 150), cam);
    sc.pointerUp();
    expect(Math.abs(sc.pose.x - 200)).toBeLessThan(3);
    expect(Math.abs(sc.pose.y - 300)).toBeLessThan(3);
    expect(sc.pose.z).toBe(300);
  });

  it('dragging the spin needle changes spin (degrees, GBH convention)', () => {
    const { sc } = start({ spin: 0 });
    // spin 0 deg is internal angle 90 deg: the needle points along +y
    expect(sc.pointerDown(at(cam, 250, 250 + 45, 100), cam)).toBe(true);
    sc.pointerMove(at(cam, 250 + 80, 250, 100), cam); // along +x: internal 0 => GBH -90
    sc.pointerUp();
    expect(Math.abs(sc.pose.spin - -90)).toBeLessThan(2);
  });

  it('dragging the tilt line changes tilt', () => {
    const { sc } = start({ spin: 0, tilt: 0 });
    expect(sc.pointerDown(at(cam, 250, 250, 100 + 45), cam)).toBe(true);
    // lean towards the spin direction (+y) by 45 deg of internal tilt (GBH tilt -45)
    sc.pointerMove(at(cam, 250, 250 + 60, 100 + 60), cam);
    sc.pointerUp();
    expect(Math.abs(sc.pose.tilt - -45)).toBeLessThan(5);
  });

  it('a pointer down far from every part is not captured', () => {
    const { sc, changes } = start({});
    expect(sc.pointerDown({ u: -400, v: -400 }, cam)).toBe(false);
    sc.pointerMove({ u: 10, v: 10 }, cam);
    expect(sc.pose.x).toBe(250);
    expect(changes.length).toBe(0);
  });

  it('an invisible cursor captures nothing', () => {
    const { sc } = start({});
    sc.visible = false;
    expect(sc.pointerDown(at(cam, 250, 250, 0), cam)).toBe(false);
  });
});

describe('pose conversions and text', () => {
  it('converts GBH degrees to Java radians', () => {
    expect(spinRad(0)).toBeCloseTo(Math.PI / 2, 12);
    expect(spinRad(-90)).toBeCloseTo(0, 12);
    expect(tiltRad(30)).toBeCloseTo(-Math.PI / 6, 12);
  });
  it('formats the red coordinate text like Pose3d.toDisplayString', () => {
    expect(formatPose({ x: 250.4, y: 249.5, z: 100, spin: 10.6, tilt: -3.2 })).toBe(
      'x=250 y=250 z=100 s=11° t=-3°',
    );
    expect(formatPose({ x: -0.2, y: 0, z: 0, spin: 0, tilt: 0 })).toBe('x=0 y=0 z=0 s=0° t=0°');
    expect(new StickCursorCoords(() => ({ x: 1, y: 2, z: 3, spin: 4, tilt: 5 })).text()).toBe(
      'x=1 y=2 z=3 s=4° t=5°',
    );
  });
});

describe('measuring overlays', () => {
  it('point measure triangulates a point from two cameras', () => {
    const c1 = new Camera(colourP);
    const c2 = new Camera(ycamP);
    const target: Vec3 = { x: 200, y: 300, z: 20 };
    const pm = new PointMeasure();
    const done: Vec3[] = [];
    pm.onMeasured = (p) => done.push(p);
    pm.enabled = true;
    expect(pm.click(at(c1, target.x, target.y, target.z), c1)).toBe(true);
    expect(pm.triangulated).toBeNull();
    // clicking again on the same camera replaces the first ray
    pm.click(at(c1, target.x, target.y, target.z), c1);
    expect(pm.triangulated).toBeNull();
    pm.click(at(c2, target.x, target.y, target.z), c2);
    expect(pm.triangulated).not.toBeNull();
    const t = pm.triangulated!;
    expect(Math.hypot(t.x - target.x, t.y - target.y, t.z - target.z)).toBeLessThan(5);
    expect(done.length).toBe(1);
  });

  it('a disabled point measure ignores clicks', () => {
    const c1 = new Camera(colourP);
    const pm = new PointMeasure();
    expect(pm.click({ u: 10, v: 10 }, c1)).toBe(false);
  });

  it('tape measure reports the distance between two measured points', () => {
    const c1 = new Camera(colourP);
    const c2 = new Camera(ycamP);
    const tm = new TapeMeasure();
    const msgs: string[] = [];
    tm.onMessage = (s) => msgs.push(s);
    tm.enabled = true;
    tm.start();
    for (const [x, y] of [
      [100, 100],
      [400, 100],
    ] as const) {
      tm.click(at(c1, x, y, 0), c1);
      tm.click(at(c2, x, y, 0), c2);
    }
    expect(tm.length()).not.toBeNull();
    expect(Math.abs(tm.length()! - 300)).toBeLessThan(8);
    expect(msgs.some((m) => m.startsWith('Measured line length '))).toBe(true);
  });

  it('path keeps the last node when cleared', () => {
    const p = new Path();
    p.addPoint({ x: 1, y: 2, z: 3 });
    p.addPoint({ x: 4, y: 5, z: 6 });
    p.clear();
    expect(p.nodes).toEqual([{ x: 4, y: 5, z: 6 }]);
    new Path().clear(); // empty: no throw
  });
});
