// Journey 3 (Review Focus 4): the canvas scales with the window, and a drag still lands
// on target, at 1600x1000 (scale above 1.5) and again at 700x500.

import { expect, test, type Page } from '@playwright/test';
import { COLOUR, devLogin, dragCursorTo, settled, shot, waitForImage } from './helpers';

async function scaleOf(page: Page): Promise<number> {
  return (await page.evaluate((c) => window.__telerobot.cameraScale(c), COLOUR))!;
}

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
