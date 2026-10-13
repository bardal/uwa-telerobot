// Journey 6: with ?fakeStatus=stopped the dev /api/status walks stopped, starting,
// running; the waking page shows "Turning the robot on…", and then the control page.

import { expect, test, type APIRequestContext } from '@playwright/test';
import { devLogin, shot } from './helpers';

const FAKE = '/api/status?fakeStatus=stopped';
type Status = { state: string; healthy: boolean; commit: string | null };

async function status(request: APIRequestContext): Promise<Status> {
  const r = await request.get(FAKE);
  expect(r.status()).toBe(200);
  return (await r.json()) as Status;
}

test('wake: a stopped lab shows "Turning the robot on…", then the control page', async ({ page, request }) => {
  // the sequence is per gateway process and cycles: get it back to its start
  for (let i = 0; i < 3 && (await status(request)).state !== 'running'; i++);

  // the dev status walks stopped, starting, running (healthy, with the commit)
  const seq = [await status(request), await status(request), await status(request)];
  expect(seq.map((s) => s.state)).toEqual(['stopped', 'starting', 'running']);
  expect(seq[0]).toEqual({ state: 'stopped', healthy: false, commit: null });
  expect(seq[2]!.healthy).toBe(true);
  expect(typeof seq[2]!.commit).toBe('string');
  // the plain status is always running
  expect(((await (await request.get('/api/status')).json()) as Status).state).toBe('running');

  // every heading the waking page shows, however briefly
  await page.addInitScript(() => {
    const seen: string[] = [];
    (window as unknown as { __headings: string[] }).__headings = seen;
    new MutationObserver(() => {
      const t = document.querySelector('.waking-heading')?.textContent ?? '';
      if (t && seen[seen.length - 1] !== t) seen.push(t);
    }).observe(document, { subtree: true, childList: true, characterData: true });
  });
  // the real lab takes minutes to start; hold the dev gateway's instant 202 a moment so the page can be seen
  await page.route('**/api/wake', async (route) => {
    await new Promise((r) => setTimeout(r, 2000));
    await route.continue();
  });
  const firstStatus = page.waitForResponse((r) => r.url().includes('/api/status'));
  const wake = page.waitForResponse((r) => r.url().endsWith('/api/wake') && r.request().method() === 'POST');

  const login = devLogin(page, 'sleepy', false, 'fakeStatus=stopped');
  expect(await (await firstStatus).json()).toMatchObject({ state: 'stopped' });
  await expect(page.locator('.waking-page')).toBeVisible();
  await expect(page.locator('.waking-heading')).toHaveText('Turning the robot on…');
  await shot(page, 'wake-1-turning-on');
  expect((await wake).status()).toBe(202);

  expect(await login).toBe('sleepy');
  await expect(page.locator('.control-page')).toBeVisible();
  await expect(page.locator('.waking-page')).toHaveCount(0);
  const headings = await page.evaluate(() => (window as unknown as { __headings: string[] }).__headings);
  expect(headings).toContain('Turning the robot on…');
  expect(headings.indexOf('Turning the robot on…')).toBeLessThan(headings.lastIndexOf('Connecting…'));
  await shot(page, 'wake-2-control-page');
});
