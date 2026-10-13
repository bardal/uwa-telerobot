import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { pointAsGBHString, poseAsGBHString, randomState, stateAsGBHString } from '../src/pathscript';

interface Fixture {
  poses: { in: [number, number, number, number, number]; pose: string; point: string; stateOpen: string; stateClosed: string }[];
}

const fixture = JSON.parse(readFileSync(new URL('./fixtures/pathscript.json', import.meta.url), 'utf8')) as Fixture;

describe('pathscript (MMoveCommand port)', () => {
  it('formats the pose (149.6,349.2,39.4,-90,0) as Java does', () => {
    expect(poseAsGBHString({ x: 149.6, y: 349.2, z: 39.4, spin: -90, tilt: 0 })).toBe('x150,y349,s-90,t0,z39');
  });

  it('has 10 Java fixture poses', () => expect(fixture.poses).toHaveLength(10));

  for (const f of fixture.poses) {
    const [x, y, z, spin, tilt] = f.in;
    it(`matches Java for ${f.in.join(',')}`, () => {
      const pose = { x, y, z, spin, tilt };
      expect(poseAsGBHString(pose)).toBe(f.pose);
      expect(pointAsGBHString({ x, y, z })).toBe(f.point);
      expect(stateAsGBHString({ pose, gripperOpen: true })).toBe(f.stateOpen);
      expect(stateAsGBHString({ pose, gripperOpen: false })).toBe(f.stateClosed);
    });
  }

  it('random states are in RobotState.random() ranges, gripper closed', () => {
    for (let i = 0; i < 50; i++) {
      const s = randomState();
      expect(s.gripperOpen).toBe(false);
      for (const v of [s.pose.x, s.pose.y, s.pose.z]) {
        expect(v).toBeGreaterThanOrEqual(0);
        expect(v).toBeLessThan(500);
      }
      for (const a of [s.pose.spin, s.pose.tilt]) {
        expect(a).toBeGreaterThanOrEqual(-45);
        expect(a).toBeLessThan(45);
      }
      expect(stateAsGBHString(s)).toMatch(/^x-?\d+,y-?\d+,s-?\d+,t-?\d+,z-?\d+,gclosed$/);
    }
  });
});
