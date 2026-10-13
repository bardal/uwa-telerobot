// Journey 4: workspace points are shared (each user's Add Point appears for both), and
// the tape measure, clicked in two camera images, gives the true distance within 2 mm.

import { expect, test, type Page } from '@playwright/test';
import { COLOUR, devLogin, dist, expectConsole, guestLogin, menu, newUser, shot, toPage, waitForImage, type Vec3 } from './helpers';

const ZCAM = 'ZCam';
const PA = { name: 'pa', x: 100, y: 120, z: 0 };
const PB = { name: 'pb', x: 380, y: 330, z: 0 };

async function addPoint(page: Page, p: Vec3 & { name: string }): Promise<void> {
  await menu(page, 'Workspace', 'Add Point');
  const d = page.getByRole('dialog', { name: 'Add Point' });
  await d.getByLabel('x', { exact: true }).fill(String(p.x));
  await d.getByLabel('y', { exact: true }).fill(String(p.y));
  await d.getByLabel('z', { exact: true }).fill(String(p.z));
  await d.getByLabel('name of point').fill(p.name);
  await d.getByRole('button', { name: 'Add' }).click();
  await expect(d).toBeHidden();
}

async function deletePoint(page: Page, name: string): Promise<void> {
  await menu(page, 'Workspace', 'Delete Point');
  const d = page.getByRole('dialog', { name: 'Delete Point' });
  await d.getByLabel('name of point').fill(name);
  await d.getByRole('button', { name: 'Delete' }).click();
}

const points = (page: Page) => page.evaluate(() => window.__telerobot.workspacePoints());
const names = async (page: Page) => ((await points(page)) ?? []).map((p) => p.name).sort();

async function showCamera(page: Page, camera: string): Promise<void> {
  await page.getByRole('button', { name: camera, exact: true }).click();
  await expect(page.locator(`canvas[aria-label="${camera} camera"]`)).toBeVisible();
  await waitForImage(page, camera);
}

async function clickAt(page: Page, camera: string, w: Vec3): Promise<void> {
  const p = await toPage(page, camera, w);
  await page.mouse.click(p.x, p.y);
}

test('workspace: Add Point is shared, and the tape measure is within 2 mm', async ({ browser }) => {
  const a = await newUser(browser);
  const b = await newUser(browser);
  try {
    await guestLogin(a.page, 'wsa');
    await devLogin(b.page, 'wsb');
    await waitForImage(a.page, COLOUR);

    // each user's Add Point appears for both
    await addPoint(a.page, PA);
    await addPoint(b.page, PB);
    for (const p of [a.page, b.page]) await expect.poll(() => names(p)).toEqual(['pa', 'pb']);
    expect((await points(b.page))!.find((p) => p.name === 'pa')).toEqual(PA);
    expect((await points(a.page))!.find((p) => p.name === 'pb')).toEqual(PB);
    await shot(a.page, 'workspace-1-points-A');
    await shot(b.page, 'workspace-1-points-B');

    // the tape measure: each end clicked in two cameras (Colour and ZCam)
    const page = a.page;
    await showCamera(page, ZCAM);
    await showCamera(page, COLOUR);
    await menu(page, 'Workspace', 'Measure line');
    await expectConsole(page, 'Click on the start of the line');
    await clickAt(page, COLOUR, PA);
    await expectConsole(page, 'Now click the same point in a second image');
    await showCamera(page, ZCAM);
    await clickAt(page, ZCAM, PA);
    await expectConsole(page, 'Now click on the end of the line');
    await clickAt(page, ZCAM, PB);
    await showCamera(page, COLOUR);
    await clickAt(page, COLOUR, PB);
    await expectConsole(page, /Measured line length \d+mm/);
    const truth = dist(PA, PB);
    const measured = (await page.evaluate(() => window.__telerobot.tapeLength()))!;
    console.log(`measure: tape ${measured.toFixed(2)} mm, true ${truth.toFixed(2)} mm`);
    expect(Math.abs(measured - truth)).toBeLessThan(2);
    const printed = Number(/Measured line length (\d+)mm/.exec(await page.locator('.controls-column textarea.console').inputValue())![1]);
    expect(Math.abs(printed - truth)).toBeLessThan(2);
    await shot(page, 'workspace-2-tape-measure');
    await menu(page, 'Workspace', 'Measure line'); // off again

    // tidy up: each owner deletes its point
    await deletePoint(a.page, 'pa');
    await deletePoint(b.page, 'pb');
    for (const p of [a.page, b.page]) await expect.poll(() => names(p)).toEqual([]);
  } finally {
    await a.context.close();
    await b.context.close();
  }
});
