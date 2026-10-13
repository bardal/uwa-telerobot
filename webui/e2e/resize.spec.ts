// Journey 3 (Review Focus 4): the canvas scales with the window, and a drag still lands
// on target, at 1600x1000 (scale above 1.5) and again at 700x500.

import { expect, test, type Page } from '@playwright/test';
import { COLOUR, devLogin, dragCursorTo, settled, shot, waitForImage } from './helpers';

async function scaleOf(page: Page): Promise<number> {
  return (await page.evaluate((c) => window.__telerobot.cameraScale(c), COLOUR))!;
}

/**
 * The shown camera's settings row: whether on, width, height, zoom, quality, fixed and the two
 * buttons share one line, and whether the row stays inside the Cameras border.
 */
async function settingsRow(page: Page): Promise<{ oneLine: boolean; inside: boolean; detail: string }> {
  return page.evaluate(() => {
    const row = [...document.querySelectorAll<HTMLElement>('.camera-controls')].find((e) => e.offsetParent !== null)!;
    const parts = [...row.querySelectorAll<HTMLElement>('.cam-cell, .cam-buttons')].map((e) => e.getBoundingClientRect());
    const tops = parts.map((r) => Math.round(r.top));
    const bottoms = parts.map((r) => Math.round(r.bottom));
    const border = row.closest('.cameras-border')!.getBoundingClientRect();
    const box = row.getBoundingClientRect();
    // one line: every part overlaps the vertical span of every other (bottom-aligned cells)
    const oneLine = Math.max(...tops) < Math.min(...bottoms);
    const inside = parts.every((r) => r.left >= border.left - 0.5 && r.right <= border.right + 0.5);
    return { oneLine, inside, detail: `row ${Math.round(box.width)} px in ${Math.round(border.width)} px; tops ${tops.join(',')}` };
  });
}

test('resize: the camera settings row stays on one line down to 700 px wide', async ({ page }) => {
  await devLogin(page, 'rowcheck');
  await waitForImage(page, COLOUR);
  for (const [w, h] of [[1280, 900], [760, 560], [700, 500]] as const) {
    await page.setViewportSize({ width: w, height: h });
    await settled(page, COLOUR);
    const r = await settingsRow(page);
    expect(r.oneLine, `at ${w}x${h}: ${r.detail}`).toBe(true);
    expect(r.inside, `at ${w}x${h}: ${r.detail}`).toBe(true);
    await shot(page, `resize-settings-row-${w}x${h}`);
  }
  // narrower still, the row wraps as a group but stays inside the border
  await page.setViewportSize({ width: 640, height: 500 });
  await settled(page, COLOUR);
  const narrow = await settingsRow(page);
  expect(narrow.inside, `at 640x500: ${narrow.detail}`).toBe(true);
});

test('resize: the canvas scales and drags still land within 3 mm', async ({ page }) => {
  await devLogin(page, 'resizer');
  await waitForImage(page, COLOUR);

  const cases = [
    { w: 1600, h: 1000, target: { x: 300, y: 300, z: 160 }, check: (s: number) => expect(s).toBeGreaterThan(1.5) },
    { w: 700, h: 500, target: { x: 200, y: 180, z: 80 }, check: (s: number) => expect(s).toBeLessThan(1.5) },
  ];
  for (const c of cases) {
    await page.setViewportSize({ width: c.w, height: c.h });
    await settled(page, COLOUR);
    const scale = await scaleOf(page);
    c.check(scale);
    const rect = (await page.evaluate((n) => window.__telerobot.canvasRect(n), COLOUR))!;
    expect(rect.right).toBeLessThanOrEqual(c.w);
    const pose = await dragCursorTo(page, COLOUR, c.target);
    console.log(`measure: ${c.w}x${c.h} scale ${scale.toFixed(3)} base error ${Math.hypot(pose.x - c.target.x, pose.y - c.target.y).toFixed(2)} mm, peak error ${Math.abs(pose.z - c.target.z).toFixed(2)} mm`);
    expect(Math.hypot(pose.x - c.target.x, pose.y - c.target.y), `base drag at ${c.w}x${c.h} (scale ${scale})`).toBeLessThan(3);
    expect(Math.abs(pose.z - c.target.z), `peak drag at ${c.w}x${c.h} (scale ${scale})`).toBeLessThan(3);
    await shot(page, `resize-${c.w}x${c.h}`);
  }
});
