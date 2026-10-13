// Journey 5: after a move and a flush (the dev-only POST /dev/flush), History shows the
// move with its 6 images; Archive Images creates a snapshot; an admin delete removes
// the entry.

import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { consoleText, devLogin, menu, release, runScript, setScript, shot, takeControl, waitForImage, COLOUR } from './helpers';

const SCRIPT = 'x210,y190,z130';

async function flush(request: APIRequestContext): Promise<void> {
  const r = await request.post('/dev/flush');
  expect(r.status()).toBe(204);
}

async function archivedId(page: Page, kind: 'move' | 'snapshot', after: number): Promise<string> {
  const re = new RegExp(`Archived ${kind} (\\S+)`, 'g');
  let id = '';
  await expect
    .poll(async () => {
      const all = [...(await consoleText(page).inputValue()).matchAll(re)];
      id = all.length > after ? all[all.length - 1]![1]! : '';
      return id;
    }, { timeout: 30_000 })
    .not.toBe('');
  return id;
}

async function countArchived(page: Page, kind: string): Promise<number> {
  return [...(await consoleText(page).inputValue()).matchAll(new RegExp(`Archived ${kind} `, 'g'))].length;
}

/** Help > History from the control page (History reloads each time it is shown). */
async function openHistory(page: Page): Promise<void> {
  if (await page.locator('.history-page').isVisible()) {
    await page.getByRole('link', { name: 'Back' }).click();
    await expect(page.locator('.control-page')).toBeVisible();
  }
  await menu(page, 'Help', 'History');
  await expect(page.locator('.history-page')).toBeVisible();
  await expect(page.locator('.history-status')).not.toHaveText('Loading…');
}

/** Every image of an entry is served as a JPEG. */
async function imagesLoad(request: APIRequestContext, entry: Locator): Promise<number> {
  const srcs = await entry.locator('img.history-thumb').evaluateAll((els) => els.map((e) => (e as HTMLImageElement).getAttribute('src')!));
  for (const s of srcs) {
    const r = await request.get(s);
    expect(r.status(), s).toBe(200);
    expect(r.headers()['content-type']).toContain('image/jpeg');
    expect((await r.body()).subarray(0, 2).toString('hex')).toBe('ffd8');
  }
  return srcs.length;
}

async function dayIndexIds(request: APIRequestContext): Promise<string[]> {
  const days = (await (await request.get('/archive/public/index/days.json')).json()) as { days: string[] };
  const ids: string[] = [];
  for (const d of days.days) {
    const idx = (await (await request.get(`/archive/public/index/${d}.json`)).json()) as { entries: { id: string }[] };
    ids.push(...idx.entries.map((e) => e.id));
  }
  return ids;
}

test('archive: a move on History with 6 images, Archive Images, and an admin delete', async ({ page, request }) => {
  // a nick of its own, so entries from earlier runs on the same stack are not counted
  const nick = await devLogin(page, `archie-${Date.now() % 100000}`, true);
  await waitForImage(page, COLOUR);

  // a move, archived once the after-images are in
  await takeControl(page, nick);
  const moves0 = await countArchived(page, 'move');
  await setScript(page, SCRIPT);
  await runScript(page);
  const moveId = await archivedId(page, 'move', moves0);
  await release(page);
  await flush(request);

  await openHistory(page);
  const move = page.locator('.history-move', { hasText: SCRIPT }).filter({ hasText: nick });
  await expect(move).toHaveCount(1);
  await expect(move.locator('.history-text')).toHaveText(`${SCRIPT} (done)`);
  await expect(move.locator('img.history-thumb')).toHaveCount(6);
  expect(await imagesLoad(request, move)).toBe(6);
  await expect(move.getByRole('button', { name: 'Delete' })).toBeVisible(); // we are an admin
  await shot(page, 'archive-1-history-move');

  // Images > Archive Images: a snapshot of the three cameras
  await page.getByRole('link', { name: 'Back' }).click();
  const snaps0 = await countArchived(page, 'snapshot');
  await menu(page, 'Images', 'Archive Images');
  const snapId = await archivedId(page, 'snapshot', snaps0);
  await flush(request);
  await openHistory(page);
  const snap = page.locator('.history-snapshot').filter({ hasText: nick });
  await expect(snap).toHaveCount(1);
  await expect(snap.locator('img.history-thumb')).toHaveCount(3);
  expect(await imagesLoad(request, snap)).toBe(3);
  expect(await dayIndexIds(request)).toEqual(expect.arrayContaining([moveId, snapId]));
  await shot(page, 'archive-2-history-snapshot');

  // the admin deletes the move: it goes from the page, the index and History
  await move.getByRole('button', { name: 'Delete' }).click();
  await expect(move).toHaveCount(0);
  await expect(page.locator('.history-status')).toHaveText('Deleted — the history updates within a minute');
  await expect.poll(async () => {
    await flush(request);
    return dayIndexIds(request);
  }).not.toContain(moveId);
  await openHistory(page);
  await expect(page.locator('.history-move', { hasText: SCRIPT }).filter({ hasText: nick })).toHaveCount(0);
  await expect(page.locator('.history-snapshot').filter({ hasText: nick })).toHaveCount(1);
  await shot(page, 'archive-3-history-after-delete');
});
