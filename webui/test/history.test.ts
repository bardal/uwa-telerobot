// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DELETED, HistoryPage, imageUrl, NOTHING } from '../src/archive/history';
import type { In } from '../src/net/protocol';

const pose = { x: 0, y: 0, z: 0, spin: 0, tilt: 0, gripperOpen: true };
const cal = Array.from({ length: 12 }, () => 0);
const mv = (cam: string) => ({
  before: `public/moves/2026/10/13/100-ab12-${cam}-before.jpg`,
  after: `public/moves/2026/10/13/100-ab12-${cam}-after.jpg`,
  calibration: cal,
});
const day = {
  day: '2026-10-13',
  entries: [
    { kind: 'move', id: '100-ab12', time: 100, nick: 'ann', level: 'guest', script: 'goto 1 2 3', result: 'done', poseBefore: pose, poseAfter: pose, frames: { colour: mv('colour'), ycam: mv('ycam'), zcam: mv('zcam') } },
    { kind: 'snapshot', id: '300-cd34', time: 300, nick: 'bob', pose, frames: { colour: 'public/snapshots/2026/10/13/300-cd34-colour.jpg', ycam: 'public/snapshots/2026/10/13/300-cd34-ycam.jpg', zcam: 'public/snapshots/2026/10/13/300-cd34-zcam.jpg' } },
    { kind: 'chat', id: '200-ef56', time: 200, nick: 'cy', text: 'hello there' },
  ],
};

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

function make(files: Record<string, () => Response | Promise<Response>>, over: { canDelete?: boolean } = {}) {
  const urls: string[] = [];
  const sent: In[] = [];
  const fetchImpl = vi.fn(async (u: string | URL | Request) => {
    const url = String(u);
    urls.push(url);
    const f = files[url];
    return f ? f() : new Response('', { status: 404 });
  }) as unknown as typeof fetch;
  const page = new HistoryPage({ fetchImpl, canDelete: () => over.canDelete ?? false, send: (m) => sent.push(m), reloadDelayMs: 5 });
  document.body.appendChild(page.el);
  return { page, urls, sent, fetchImpl };
}

const DAYS = '/archive/public/index/days.json';
const DAY = '/archive/public/index/2026-10-13.json';

beforeEach(() => document.body.replaceChildren());

describe('History page', () => {
  it('renders a move (6 images), a snapshot (3) and a chat line, newest first', async () => {
    const { page } = make({ [DAYS]: () => json({ days: ['2026-10-13'] }), [DAY]: () => json(day) });
    await page.load();
    expect(page.list.querySelectorAll('img')).toHaveLength(9);
    expect(page.list.querySelectorAll('.history-move img')).toHaveLength(6);
    expect(page.list.querySelectorAll('.history-snapshot img')).toHaveLength(3);
    expect(page.list.querySelectorAll('.history-chat')).toHaveLength(1);
    expect([...page.list.children].map((c) => c.className.split(' ')[1])).toEqual(['history-snapshot', 'history-chat', 'history-move']);
    expect(page.list.querySelector('img')!.getAttribute('src')).toBe('/archive/public/snapshots/2026/10/13/300-cd34-colour.jpg');
    expect(page.list.textContent).toContain('hello there');
  });

  it('clicking a thumbnail shows it full size; clicking again closes', async () => {
    const { page } = make({ [DAYS]: () => json({ days: ['2026-10-13'] }), [DAY]: () => json(day) });
    await page.load();
    (page.list.querySelector('img') as HTMLImageElement).click();
    const full = page.el.querySelector('.history-viewer') as HTMLElement;
    expect(full.hidden).toBe(false);
    expect(full.querySelector('img')!.getAttribute('src')).toContain('/archive/public/snapshots/');
    full.click();
    expect(full.hidden).toBe(true);
  });

  it('hostile entries are skipped or shown as text', async () => {
    const hostile = {
      day: '2026-10-13',
      entries: [
        { kind: 'snapshot', id: 'x', time: 1, nick: '<img src=x onerror=alert(1)>', frames: { colour: 'https://evil/x.jpg', ycam: 'public/snapshots/../../etc/x.jpg', zcam: 'public/snapshots/2026/10/13/ok-1.jpg' } },
        { kind: 'chat', id: 'y', time: 2, nick: 'e', text: '<script>alert(1)</script>' },
        { kind: 'move', id: 'z', time: 3, nick: 'm', script: '<b>x</b>', result: 'done', frames: { colour: { before: 'javascript:alert(1)', after: '//evil/a.jpg' } } },
        { kind: 'weird', id: 'w', time: 4 },
        'junk',
      ],
    };
    const { page } = make({ [DAYS]: () => json({ days: ['2026-10-13', '../x', '2026-10-12/../../y'] }), [DAY]: () => json(hostile) });
    await page.load();
    const srcs = [...page.list.querySelectorAll('img')].map((i) => i.getAttribute('src'));
    expect(srcs).toEqual(['/archive/public/snapshots/2026/10/13/ok-1.jpg']);
    expect(page.list.querySelector('script')).toBeNull();
    expect(page.list.querySelector('b')).toBeNull();
    expect(page.list.textContent).toContain('<script>alert(1)</script>');
    expect([...page.daySelect.options].map((o) => o.value)).toEqual(['2026-10-13']);
    expect(imageUrl('https://evil/x.jpg')).toBeNull();
    expect(imageUrl('public/moves/2026/10/13/1-ab-colour-before.jpg')).not.toBeNull();
  });

  it('a missing day shows "Nothing archived on this day"', async () => {
    const { page } = make({ [DAYS]: () => json({ days: ['2026-10-13'] }) });
    await page.load();
    expect(page.status.textContent).toBe(NOTHING);
  });

  it('network errors show a message, never a stack', async () => {
    const { page } = make({ [DAYS]: () => { throw new TypeError('Failed to fetch at x.js:1'); } });
    await page.load();
    expect(page.status.textContent).toBe('Could not load the history. Please try again later.');
    const b = make({ [DAYS]: () => json({ days: ['2026-10-13'] }), [DAY]: () => new Response('', { status: 503 }) });
    await b.page.load();
    expect(b.page.status.textContent).toContain('Could not load this day');
  });

  it('fetches use a timeout signal and only static index URLs', async () => {
    const { page, fetchImpl, urls } = make({ [DAYS]: () => json({ days: ['2026-10-13'] }), [DAY]: () => json(day) });
    await page.load();
    expect(urls).toEqual([DAYS, DAY]);
    expect((fetchImpl as unknown as ReturnType<typeof vi.fn>).mock.calls[0]![1].signal).toBeInstanceOf(AbortSignal);
  });

  it('never reads an index from the browser cache, so a reload after a flush or a delete is fresh', async () => {
    const { page, fetchImpl } = make({ [DAYS]: () => json({ days: ['2026-10-13'] }), [DAY]: () => json(day) });
    await page.load();
    const calls = (fetchImpl as unknown as ReturnType<typeof vi.fn>).mock.calls;
    expect(calls.map((c) => c[1].cache)).toEqual(['no-store', 'no-store']);
  });

  it('shows no Delete buttons unless allowed', async () => {
    const { page } = make({ [DAYS]: () => json({ days: ['2026-10-13'] }), [DAY]: () => json(day) });
    await page.load();
    expect(page.list.querySelector('.history-delete')).toBeNull();
  });

  it('admin Delete sends the admin message, then reloads the day after a delay', async () => {
    const { page, sent, urls } = make({ [DAYS]: () => json({ days: ['2026-10-13'] }), [DAY]: () => json(day) }, { canDelete: true });
    await page.load();
    const del = (cls: string) => page.list.querySelector(`.${cls} .history-delete`) as HTMLButtonElement;
    del('history-move').click();
    del('history-snapshot').click();
    del('history-chat').click();
    expect(sent).toEqual([
      { t: 'admin', action: 'deleteMove', id: '100-ab12' },
      { t: 'admin', action: 'deleteSnapshot', id: '300-cd34' },
      { t: 'admin', action: 'deleteChat', id: '200-ef56' },
    ]);
    expect(page.status.textContent).toBe(DELETED);
    expect(page.list.children).toHaveLength(0);
    await new Promise((r) => setTimeout(r, 30));
    expect(urls.filter((u) => u === DAY)).toHaveLength(2);
    expect(page.status.textContent).toBe(DELETED);
  });
});
