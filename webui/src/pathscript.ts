// Port of devices/robot/MMoveCommand's GBH string builders and RobotState.random().
// Spin and tilt are given in degrees (as the cursor and robotState carry them) but
// Java stores them as radians and converts back with Orientation3d.spinDeg()/tiltDeg();
// the round trip is repeated here in the same operation order so that halves round
// exactly as they do in Java.

import type { Pose } from './overlays/types';

export type Point = { x: number; y: number; z: number };
export type State = { pose: Pose; gripperOpen: boolean };

const SPIN_OFFSET = -90; // Orientation3d.spinOffset

/** Orientation3d.spinDeg(s) then spinDeg(). */
function javaSpinDeg(s: number): number {
  const rad = ((s - SPIN_OFFSET) / 180) * Math.PI;
  return rad * (180 / Math.PI) + SPIN_OFFSET;
}

/** Orientation3d.tiltDeg(t) then tiltDeg(). */
function javaTiltDeg(t: number): number {
  const rad = (-t / 180) * Math.PI;
  return (-rad * 180) / Math.PI;
}

/** Java's Math.round(double): half up; JS agrees, and String(-0) is "0" as Java's long prints. */
const round = (v: number): string => String(Math.round(v));

export function poseAsGBHString(pose: Pose): string {
  return (
    `x${round(pose.x)}` +
    `,y${round(pose.y)}` +
    `,s${round(javaSpinDeg(pose.spin))}` +
    `,t${round(javaTiltDeg(pose.tilt))}` +
    `,z${round(pose.z)}`
  );
}

export function pointAsGBHString(p: Point): string {
  return `x${round(p.x)},y${round(p.y)},z${round(p.z)}`;
}

/** Gripper.toString() is "open" or "closed". */
export function stateAsGBHString(s: State): string {
  return `${poseAsGBHString(s.pose)},g${s.gripperOpen ? 'open' : 'closed'}`;
}

/** RobotState.random(): position in [0,500) mm, spin and tilt in [-45,45) degrees, gripper closed. */
export function randomState(rnd: () => number = Math.random): State {
  return {
    pose: {
      x: rnd() * 500,
      y: rnd() * 500,
      z: rnd() * 500,
      spin: rnd() * 90 - 45,
      tilt: rnd() * 90 - 45,
    },
    gripperOpen: false,
  };
}
