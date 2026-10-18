// Shared steps for the journeys. Pixel targets come from the page's own camera
// (window.__telerobot, exposed with ?e2e=1): the display-scaled 3x4 P projects a
// world point, and the canvas rectangle turns it into page coordinates.

import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, type Browser, type BrowserContext, type Page } from '@playwright/test';

export type Vec3 = { x: number; y: number; z: number };
export type Pose = Vec3 & { spin: number; tilt: number };
type Rect = { x: number; y: number; width: number; height: number; left: number; top: number; right: number; bottom: number };
type RobotState = Pose & { gripperOpen: boolean; moving: boolean };

interface Hook {
  cameraMatrix(name: string): number[] | null;
  cameraScale(name: string): number | null;
  canvasRect(name: string): Rect | null;
  cursorPose(): Pose | null;
  robotState(): RobotState | null;
  controller(): string | null;
  nick(): string | null;
  workspacePoints(): (Vec3 & { name: string })[] | null;
  tapeLength(): number | null;
}
declare global {
  interface Window { __telerobot: Hook }
}

const here = dirname(fileURLToPath(import.meta.url));
export const RESULTS = join(here, '..', 'e2e-results');

export const PICK_AND_PLACE = 'x120,y150,z60,s-90,t0,g0;z15;g1;z80;x330,y120;z45;g0;z90';
export const COLOUR = 'Colour';

/** A screenshot in e2e-results/<name>.png. */
export async function shot(page: Page, name: string): Promise<void> {
  mkdirSync(RESULTS, { recursive: true });
  await page.screenshot({ path: join(RESULTS, `${name}.png`) });
}

export async function open(page: Page, query = ''): Promise<void> {
  await page.goto(`/?e2e=1${query ? `&${query}` : ''}#/login`);
  // the config is loaded once the Guest button is enabled
  await expect(page.getByRole('button', { name: 'Guest', exact: true })).toBeEnabled();
}

async function welcomed(page: Page): Promise<string> {
  await expect.poll(() => page.evaluate(() => window.__telerobot.nick()), { timeout: 60_000 }).not.toBeNull();
  return (await page.evaluate(() => window.__telerobot.nick()))!;
}

/** Guest login through Turnstile (the test site key passes on its own). Resolves with the gateway's nick. */
export async function guestLogin(page: Page, nick: string, query = ''): Promise<string> {
  await open(page, query);
  await page.getByLabel('nickname').fill(nick);
  await page.getByRole('button', { name: 'Guest', exact: true }).click();
  return welcomed(page);
}

/** The dev sign-in: a registered user, or an admin with the box ticked. */
export async function devLogin(page: Page, nick: string, admin = false, query = ''): Promise<string> {
  await open(page, query);
  await page.getByLabel('nickname').fill(nick);
  if (admin) await page.getByLabel('admin', { exact: true }).check();
  await page.getByRole('button', { name: 'Dev sign in' }).click();
  return welcomed(page);
}

export async function newUser(browser: Browser): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext();
  const page = await context.newPage();
  return { context, page };
}

/** Waits for a frame on that camera: its matrix exists once an image was shown. */
export async function waitForImage(page: Page, camera: string): Promise<void> {
  await expect.poll(() => page.evaluate((c) => window.__telerobot.cameraMatrix(c), camera), { timeout: 30_000 }).not.toBeNull();
  await settled(page, camera);
}

/**
 * Waits until a shown camera's layout has caught up (its canvas has a size, and the
 * rectangle and matrix are the same across two animation frames). A card that was just
 * shown is laid out by its ResizeObserver, so until then its matrix is the hidden one's.
 */
export async function settled(page: Page, camera: string): Promise<void> {
  let last = '';
  await expect
    .poll(async () => {
      const now = await page.evaluate(async (c) => {
        await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
        const r = window.__telerobot.canvasRect(c);
        return r && r.width > 0 ? JSON.stringify([r, window.__telerobot.cameraMatrix(c)]) : '';
      }, camera);
      const same = now !== '' && now === last;
      last = now;
      return same;
    }, { intervals: [100], timeout: 15_000 })
    .toBe(true);
}

/** Distinct colours among a sample of the canvas pixels (a blank canvas has one or two). */
export async function canvasColours(page: Page, camera: string): Promise<number> {
  return page.evaluate((c) => {
    const el = document.querySelector<HTMLCanvasElement>(`canvas[aria-label="${c} camera"]`);
    const ctx = el?.getContext('2d');
    if (!el || !ctx) return 0;
    const d = ctx.getImageData(0, 0, el.width, el.height).data;
    const seen = new Set<number>();
    for (let i = 0; i < d.length; i += 4 * 97) seen.add((d[i]! << 16) | (d[i + 1]! << 8) | d[i + 2]!);
    return seen.size;
  }, camera);
}

/** Page coordinates of a world point as the page's camera projects it. */
export async function toPage(page: Page, camera: string, w: Vec3): Promise<{ x: number; y: number }> {
  const { P, r } = await page.evaluate((c) => ({ P: window.__telerobot.cameraMatrix(c), r: window.__telerobot.canvasRect(c) }), camera);
  if (!P || !r) throw new Error(`no camera ${camera} yet`);
  const X = [w.x, w.y, w.z, 1];
  const row = (i: number) => P[4 * i]! * X[0]! + P[4 * i + 1]! * X[1]! + P[4 * i + 2]! * X[2]! + P[4 * i + 3]! * X[3]!;
  const s = row(2);
  return { x: r.left + row(0) / s, y: r.top + row(1) / s };
}

export async function drag(page: Page, from: { x: number; y: number }, to: { x: number; y: number }, steps = 10): Promise<void> {
  await page.mouse.move(from.x, from.y);
  await page.mouse.down();
  await page.mouse.move(to.x, to.y, { steps });
  await page.mouse.up();
}

export const cursorPose = (page: Page): Promise<Pose> => page.evaluate(() => window.__telerobot.cursorPose()!);
export const robotState = (page: Page): Promise<RobotState | null> => page.evaluate(() => window.__telerobot.robotState());
export const controller = (page: Page): Promise<string | null> => page.evaluate(() => window.__telerobot.controller());

/** Drags the base marker to (x, y) on the table, then the peak to height z. */
export async function dragCursorTo(page: Page, camera: string, target: Vec3): Promise<Pose> {
  const p0 = await cursorPose(page);
  await drag(page, await toPage(page, camera, { x: p0.x, y: p0.y, z: 0 }), await toPage(page, camera, { x: target.x, y: target.y, z: 0 }));
  const p1 = await cursorPose(page);
  await drag(page, await toPage(page, camera, { x: p1.x, y: p1.y, z: p1.z }), await toPage(page, camera, { x: p1.x, y: p1.y, z: target.z }));
  return cursorPose(page);
}

export const dist = (a: Vec3, b: Vec3): number => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);

/** Control Robot, then wait until the gateway names us the controller. */
export async function takeControl(page: Page, nick: string): Promise<void> {
  await page.getByRole('button', { name: 'Control Robot' }).click();
  await expect.poll(() => controller(page)).toBe(nick);
  await expect(page.getByRole('button', { name: 'Release Robot' })).toBeVisible();
}

/** Release Robot if this page has control (each journey ends this way). */
export async function release(page: Page): Promise<void> {
  if (page.isClosed()) return;
  const us = await page.evaluate(() => window.__telerobot.nick() !== null && window.__telerobot.controller() === window.__telerobot.nick()).catch(() => false);
  if (!us) return;
  await page.getByRole('button', { name: 'Release Robot' }).click();
  await expect.poll(() => controller(page)).not.toBe(await page.evaluate(() => window.__telerobot.nick()));
}

/** Waits until the robot has stopped (after a command finished). */
export async function waitIdle(page: Page, timeout = 60_000): Promise<RobotState> {
  await expect.poll(async () => (await robotState(page))?.moving, { timeout }).toBe(false);
  return (await robotState(page))!;
}

/** Types a script into the command editor, replacing what was there. */
export async function setScript(page: Page, script: string): Promise<void> {
  await page.getByRole('button', { name: 'Clear' }).click();
  await page.getByLabel('Command editor').fill(script);
}

export async function menu(page: Page, title: string, itemLabel: string): Promise<void> {
  await page.locator('.menu-title', { hasText: new RegExp(`^${title}$`) }).click();
  await page.locator('.menu-drop:not([hidden]) .menu-item', { hasText: itemLabel }).first().click();
}

export function consoleText(page: Page) {
  return page.locator('.controls-column textarea.console');
}

/** Console text contains s, eventually. */
export async function expectConsole(page: Page, s: string | RegExp, timeout = 30_000): Promise<void> {
  await expect.poll(() => consoleText(page).inputValue(), { timeout }).toMatch(typeof s === 'string' ? new RegExp(s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')) : s);
}

/**
 * Resets the lab (blocks back on their marks, the arm home) through the admin `reset`
 * action, from a dev admin sign-in. A journey's setup step, so it skips the UI (admin.spec.ts
 * drives the Admin menu): the admin's hello and the action go over a WebSocket opened in a
 * page on the gateway's origin.
 */
export async function resetLab(browser: Browser): Promise<void> {
  const { context, page } = await newUser(browser);
  try {
    await page.goto('/healthz');
    const text = await page.evaluate(
      () =>
        new Promise<string>((resolve, reject) => {
          const ws = new WebSocket(`ws://${location.host}/ws`);
          const t = setTimeout(() => { ws.close(); reject(new Error('reset timed out')); }, 20_000);
          ws.onopen = () => ws.send(JSON.stringify({ t: 'hello', nick: 'resetter', dev: { sub: 'dev-resetter', groups: ['admin'] } }));
          ws.onmessage = (ev) => {
            if (typeof ev.data !== 'string') return;
            const m = JSON.parse(ev.data) as { t: string; text?: string; message?: string };
            if (m.t === 'welcome') ws.send(JSON.stringify({ t: 'admin', action: 'reset' }));
            else if (m.t === 'console' && m.text === 'The lab has been reset') { clearTimeout(t); ws.close(); resolve(m.text); }
            else if (m.t === 'error') { clearTimeout(t); ws.close(); reject(new Error(m.message)); }
          };
        }),
    );
    expect(text).toBe('The lab has been reset');
  } finally {
    await context.close();
  }
}

const DONE_RE = /Command \d+ completed successfully/g;
const PROBLEM_RE = /Command \d+ had a problem :.*/g;

/** Presses Send command and waits for its "completed successfully" line and the arm to stop. */
export async function runScript(page: Page, timeout = 60_000): Promise<RobotState> {
  const before = await consoleText(page).inputValue();
  const done0 = (before.match(DONE_RE) ?? []).length;
  const bad0 = (before.match(PROBLEM_RE) ?? []).length;
  await page.getByRole('button', { name: 'Send command' }).click();
  await expect
    .poll(async () => {
      const t = await consoleText(page).inputValue();
      const bad = t.match(PROBLEM_RE) ?? [];
      if (bad.length > bad0) return `error: ${bad[bad.length - 1]}`;
      return (t.match(DONE_RE) ?? []).length > done0 ? 'done' : 'waiting';
    }, { timeout })
    .toBe('done');
  return waitIdle(page, timeout);
}

/** "Robot Position :Stopped x=330 y=120 z=90 s=..": the numbers on the Robot Position line. */
export async function positionLine(page: Page): Promise<{ moving: boolean; x: number; y: number; z: number } | null> {
  const t = (await page.locator('.position-line').textContent()) ?? '';
  const m = /:(Moving|Stopped) x=(-?\d+) y=(-?\d+) z=(-?\d+)/.exec(t);
  return m ? { moving: m[1] === 'Moving', x: Number(m[2]), y: Number(m[3]), z: Number(m[4]) } : null;
}

/** Records every CSP violation on the page (read with cspViolations). */
export async function watchCsp(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const seen: string[] = [];
    (window as unknown as { __csp: string[] }).__csp = seen;
    document.addEventListener('securitypolicyviolation', (e) => seen.push(`${e.effectiveDirective} ${e.blockedURI}`));
  });
}

export const cspViolations = (page: Page): Promise<string[]> =>
  page.evaluate(() => (window as unknown as { __csp?: string[] }).__csp ?? []);
