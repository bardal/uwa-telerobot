import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { Camera, displayMatrix, type P34, type Vec3 } from '../src/geometry/camera';

type Row3 = [number, number, number];
interface Fixture {
  cameras: {
    name: string;
    P: number[][];
    points: { xyz: Row3; uv: [number, number] }[];
    lines: { uv: [number, number]; a: Row3; b: Row3 }[];
    plane: { uv: [number, number]; z0: Row3 | null; z40: Row3 | null }[];
    display: { scale: number; offset: [number, number]; P: number[][]; points: { xyz: Row3; uv: [number, number] }[] };
  }[];
}

const fixture = JSON.parse(
  readFileSync(new URL('./fixtures/camera.json', import.meta.url), 'utf8'),
) as Fixture;
const flat = (m: number[][]): P34 => m.flat();
const v = (a: Row3): Vec3 => ({ x: a[0], y: a[1], z: a[2] });
const unit = (a: Vec3, b: Vec3): Vec3 => {
  const dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
  const n = Math.hypot(dx, dy, dz);
  return { x: dx / n, y: dy / n, z: dz / n };
};

describe('camera geometry vs Java fixtures', () => {
  it('has cameras', () => expect(fixture.cameras.length).toBeGreaterThan(0));

  for (const c of fixture.cameras) {
    describe(c.name, () => {
      const cam = new Camera(flat(c.P));

      it('xyz2uv within 0.5 px', () => {
        for (const p of c.points) {
          const uv = cam.xyz2uv(v(p.xyz));
          expect(Math.abs(uv.u - p.uv[0])).toBeLessThanOrEqual(0.5);
          expect(Math.abs(uv.v - p.uv[1])).toBeLessThanOrEqual(0.5);
        }
      });

      it('uv2line matches after normalising direction', () => {
        for (const l of c.lines) {
          const got = cam.uv2line({ u: l.uv[0], v: l.uv[1] });
          const want = unit(v(l.a), v(l.b));
          const have = unit(got.a, got.b);
          expect(Math.abs(have.x - want.x)).toBeLessThan(1e-6);
          expect(Math.abs(have.y - want.y)).toBeLessThan(1e-6);
          expect(Math.abs(have.z - want.z)).toBeLessThan(1e-6);
          // both rays start at the optical centre
          expect(Math.hypot(got.a.x - l.a[0], got.a.y - l.a[1], got.a.z - l.a[2])).toBeLessThan(0.01);
        }
      });

      it('uv2xyzPlane within 0.01 mm', () => {
        for (const p of c.plane) {
          const uv = { u: p.uv[0], v: p.uv[1] };
          for (const [want, d] of [[p.z0, 0], [p.z40, -40]] as const) {
            const got = cam.uv2xyzPlane(uv, 0, 0, 1, d);
            expect(got).not.toBeNull();
            expect(want).not.toBeNull();
            if (got && want) {
              expect(Math.hypot(got.x - want[0], got.y - want[1], got.z - want[2])).toBeLessThan(0.01);
            }
          }
        }
      });

      it('uv2xyzSurface lands on z = 0', () => {
        for (const p of c.plane) {
          const got = cam.uv2xyzSurface({ u: p.uv[0], v: p.uv[1] }, () => 0);
          expect(got).not.toBeNull();
          if (got && p.z0) {
            expect(Math.hypot(got.x - p.z0[0], got.y - p.z0[1], got.z - p.z0[2])).toBeLessThan(0.01);
          }
        }
      });

      it('display matrix projects within 0.5 px', () => {
        const d = c.display;
        const dp = displayMatrix(flat(c.P), d.scale, d.offset[0], d.offset[1]);
        const want = flat(d.P);
        dp.forEach((x, i) => expect(Math.abs(x - want[i]!)).toBeLessThan(1e-6));
        const shown = new Camera(dp);
        for (const p of d.points) {
          const uv = shown.xyz2uv(v(p.xyz));
          expect(Math.abs(uv.u - p.uv[0])).toBeLessThanOrEqual(0.5);
          expect(Math.abs(uv.v - p.uv[1])).toBeLessThanOrEqual(0.5);
        }
      });

      it('optical centre projects to zero', () => {
        const o = cam.centre();
        const p = flat(c.P);
        for (let r = 0; r < 3; r++) {
          const w = p[r * 4]! * o.x + p[r * 4 + 1]! * o.y + p[r * 4 + 2]! * o.z + p[r * 4 + 3]!;
          expect(Math.abs(w)).toBeLessThan(1e-6);
        }
      });
    });
  }
});

describe('inFront', () => {
  for (const c of fixture.cameras) {
    it(`${c.name}: workspace points are in front, their reflection through the centre is not`, () => {
      const cam = new Camera(flat(c.P));
      const o = cam.centre();
      for (const p of c.points) {
        const w = v(p.xyz);
        expect(cam.inFront(w)).toBe(true);
        const behind = { x: 2 * o.x - w.x, y: 2 * o.y - w.y, z: 2 * o.z - w.z };
        expect(cam.inFront(behind)).toBe(false);
        expect(cam.depth(behind)).toBeCloseTo(-cam.depth(w), 6);
      }
      expect(cam.inFront(o)).toBe(false);
    });
  }
});
