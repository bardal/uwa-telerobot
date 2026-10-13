// Journey 1, after AppletWalkthrough: a guest logs in through Turnstile, sees the
// images, takes control, drags the stick cursor, commands the robot to it, and
// runs the pick-and-place. A screenshot is saved at each step.

import { expect, test } from '@playwright/test';
import {
  COLOUR, PICK_AND_PLACE, canvasColours, cspViolations, watchCsp, cursorPose, dist, dragCursorTo, guestLogin, menu, positionLine,
  release, resetLab, robotState, runScript, setScript, shot, takeControl, waitForImage,
} from './helpers';

const TARGET = { x: 320, y: 180, z: 140 };

test('walkthrough: guest login, images, control, cursor drag, command from cursor, pick-and-place', async ({ page, browser }) => {
  await resetLab(browser);
  await watchCsp(page);

  // log in as a guest through Turnstile's test key
  const nick = await guestLogin(page, 'walk');
  expect(nick).toBe('walk');
  await shot(page, 'walkthrough-1-logged-in');

  // the images appear: the canvas has real pixels, not one background colour
  await waitForImage(page, COLOUR);
  await expect.poll(() => canvasColours(page, COLOUR)).toBeGreaterThan(50);
  await shot(page, 'walkthrough-2-images');

  // Colour, then Control Robot: the Controller line names us
  await page.getByRole('button', { name: COLOUR, exact: true }).click();
  await takeControl(page, nick);
  await expect(page.locator('.controller-line')).toHaveText('Controller : walk');
  await shot(page, 'walkthrough-3-in-control');

  // drag the cursor's base, then its peak, where the page's camera projects them
  const pose = await dragCursorTo(page, COLOUR, TARGET);
  expect(Math.hypot(pose.x - TARGET.x, pose.y - TARGET.y), 'base drag lands on target').toBeLessThan(3);
  expect(Math.abs(pose.z - TARGET.z), 'peak drag lands on target').toBeLessThan(3);
  await shot(page, 'walkthrough-4-cursor-dragged');

  // Robot > Comand from cursor, then Send command: the robot reaches the cursor
  await page.getByRole('button', { name: 'Clear' }).click();
  await menu(page, 'Robot', 'Comand from cursor');
  const cursor = await cursorPose(page);
  await expect(page.getByLabel('Command editor')).toHaveValue(/^x\d+,y\d+,s-?\d+,t-?\d+,z\d+$/);
  await runScript(page);
  await expect.poll(async () => {
    const p = await positionLine(page);
    return p && !p.moving ? dist(p, cursor) : Infinity;
  }).toBeLessThan(3);
  expect(dist((await robotState(page))!, cursor)).toBeLessThan(3);
  await shot(page, 'walkthrough-5-at-cursor');

  // the pick-and-place: the robot ends at x=330 y=120 z=90
  await setScript(page, PICK_AND_PLACE);
  await runScript(page);
  await expect.poll(async () => {
    const s = await robotState(page);
    return s && !s.moving ? dist(s, { x: 330, y: 120, z: 90 }) : Infinity;
  }, { timeout: 60_000 }).toBeLessThan(1);
  await expect.poll(() => positionLine(page)).toEqual({ moving: false, x: 330, y: 120, z: 90 });
  await shot(page, 'walkthrough-6-pick-and-place');

  await release(page);
  // the whole journey, Turnstile included, ran inside the policy
  expect(await cspViolations(page)).toEqual([]);
});
