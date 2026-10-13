// The History page: reads the static archive index (days.json, then one day's
// index) and renders moves, snapshots and chat, newest first. It only reads
// static files, so it works while the lab is asleep and never triggers a wake.
// All text goes in through textContent; image URLs come only from vetted keys.

import type { In } from '../net/protocol';
import { button, h } from '../ui/dom';

export const KEY_RE = /^public\/(moves|snapshots)\/[0-9/]+\/[a-z0-9-]+\.jpg$/;
const DAY_RE = /^\d{4}-\d{2}-\d{2}$/;
const CAMERAS = ['colour', 'ycam', 'zcam'];
export const NOTHING = 'Nothing archived on this day';
export const DELETED = 'Deleted — the history updates within a minute';
const FETCH_TIMEOUT_MS = 10000;
/** Thumbnail size: the cameras' 400x300 at 120 wide. */
export const THUMB_WIDTH = 120;
export const THUMB_HEIGHT = 90;
const MISSING = Symbol('missing');
const FAILED = Symbol('failed');

export interface HistoryDeps {
  fetchImpl: typeof fetch;
  /** True only for an admin session with an open gateway connection. */
  canDelete: () => boolean;
  send: (m: In) => void;
  /** Delay before the day is reloaded after a delete. */
  reloadDelayMs?: number;
}

type Rec = Record<string, unknown>;
const isRec = (v: unknown): v is Rec => typeof v === 'object' && v !== null && !Array.isArray(v);
const str = (v: unknown): string => (typeof v === 'string' ? v : '');

/** The URL for an archive key, or null when the key is not an archive image key. */
export function imageUrl(key: unknown): string | null {
  return typeof key === 'string' && KEY_RE.test(key) ? `/archive/${key}` : null;
}

export class HistoryPage {
  readonly el: HTMLElement;
  readonly daySelect: HTMLSelectElement;
  readonly status: HTMLElement;
  readonly list: HTMLElement;
  private readonly viewer: HTMLElement;
  private run = 0;
  private reloadTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly deps: HistoryDeps) {
    this.daySelect = h('select', { 'aria-label': 'Day', class: 'history-day' });
    this.daySelect.addEventListener('change', () => void this.loadDay(this.daySelect.value));
    this.status = h('div', { class: 'awt-label history-status', role: 'status' });
    this.list = h('div', { class: 'history-list' });
    this.viewer = h('div', { class: 'history-viewer', hidden: true });
    this.viewer.addEventListener('click', () => this.closeViewer());
    this.el = h(
      'div',
      { class: 'page history-page' },
      h(
        'fieldset',
        { class: 'border-panel history-box' },
        h('legend', { text: 'History' }),
        h('div', { class: 'history-bar' }, this.daySelect, h('a', { href: '#/', class: 'back-link', text: 'Back' })),
        this.status,
        this.list,
      ),
      this.viewer,
    );
  }

  /** Loads the list of days, then the newest one. */
  async load(): Promise<void> {
    const run = ++this.run;
    this.status.textContent = 'Loading…';
    this.list.replaceChildren();
    const days = await this.getJson('/archive/public/index/days.json');
    if (run !== this.run) return;
    if (days === MISSING || days === FAILED) {
      this.status.textContent = days === MISSING ? 'Nothing archived yet' : 'Could not load the history. Please try again later.';
      this.daySelect.replaceChildren();
      return;
    }
    const list = isRec(days) && Array.isArray(days.days) ? days.days.filter((d): d is string => typeof d === 'string' && DAY_RE.test(d)) : [];
    this.daySelect.replaceChildren(...list.map((d) => h('option', { value: d, text: d })));
    if (!list.length) {
      this.status.textContent = 'Nothing archived yet';
      return;
    }
    await this.loadDay(list[0]!);
  }

  /** Loads one day's index (the day must look like YYYY-MM-DD). */
  async loadDay(day: string, keepStatus = ''): Promise<void> {
    if (!DAY_RE.test(day)) return;
    const run = ++this.run;
    if (this.daySelect.value !== day) this.daySelect.value = day;
    this.status.textContent = keepStatus || 'Loading…';
    this.list.replaceChildren();
    const idx = await this.getJson(`/archive/public/index/${day}.json`);
    if (run !== this.run) return;
    if (idx === MISSING) {
      this.status.textContent = NOTHING;
      return;
    }
    if (idx === FAILED) {
      this.status.textContent = 'Could not load this day. Please try again later.';
      return;
    }
    const entries = isRec(idx) && Array.isArray(idx.entries) ? idx.entries.filter(isRec) : [];
    entries.sort((a, b) => (Number(b.time) || 0) - (Number(a.time) || 0));
    const els = entries.map((e) => this.entry(e)).filter((e): e is HTMLElement => e !== null);
    if (!els.length) {
      this.status.textContent = NOTHING;
      return;
    }
    this.status.textContent = keepStatus;
    this.list.replaceChildren(...els);
  }

  dispose(): void {
    this.run++;
    if (this.reloadTimer) clearTimeout(this.reloadTimer);
    this.reloadTimer = null;
  }

  private async getJson(url: string): Promise<unknown> {
    try {
      // never from the browser cache: an index rewritten within the second of its Last-Modified
      // would revalidate as 304 Not Modified, and History would miss the new entry or the delete
      const r = await this.deps.fetchImpl(url, { cache: 'no-store', signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
      if (r.status === 404) return MISSING;
      if (!r.ok) return FAILED;
      return (await r.json()) as unknown;
    } catch {
      return FAILED;
    }
  }

  private entry(e: Rec): HTMLElement | null {
    const kind = str(e.kind);
    if (kind !== 'move' && kind !== 'snapshot' && kind !== 'chat') return null;
    const id = str(e.id);
    const time = Number(e.time);
    const when = Number.isFinite(time) && time > 0 ? new Date(time).toLocaleString() : '';
    const head = h('div', { class: 'history-head' }, h('span', { class: 'history-time', text: when }), h('span', { class: 'history-nick', text: str(e.nick) }));
    const row = h('div', { class: `history-entry history-${kind}` }, head);
    const frames = isRec(e.frames) ? e.frames : {};
    if (kind === 'chat') {
      row.appendChild(h('div', { class: 'history-text', text: str(e.text) }));
    } else if (kind === 'move') {
      row.appendChild(h('div', { class: 'history-text', text: `${str(e.script)}${e.result ? ` (${str(e.result)})` : ''}` }));
      for (const cam of CAMERAS) {
        const f = frames[cam];
        if (isRec(f)) row.appendChild(this.thumbs(cam, [['before', f.before], ['after', f.after]]));
      }
    } else {
      for (const cam of CAMERAS) row.appendChild(this.thumbs(cam, [['', frames[cam]]]));
    }
    if (id && this.deps.canDelete()) {
      const action = kind === 'move' ? 'deleteMove' : kind === 'snapshot' ? 'deleteSnapshot' : 'deleteChat';
      head.appendChild(
        button('Delete', () => {
          if (!this.deps.canDelete()) return;
          this.deps.send({ t: 'admin', action, id });
          row.remove();
          this.status.textContent = DELETED;
          this.scheduleReload();
        }, 'history-delete'),
      );
    }
    return row;
  }

  private thumbs(cam: string, items: [string, unknown][]): HTMLElement {
    const box = h('div', { class: 'history-thumbs' });
    for (const [which, key] of items) {
      const url = imageUrl(key);
      if (!url) continue;
      const label = which ? `${cam} ${which}` : cam;
      // a fixed 120x90 box (the cameras' 4:3), so the layout holds still while each JPEG loads
      const img = h('img', {
        src: url, alt: label, title: label, loading: 'lazy', class: 'history-thumb',
        width: THUMB_WIDTH, height: THUMB_HEIGHT,
      });
      img.addEventListener('click', () => this.openViewer(url, label));
      box.appendChild(img);
    }
    return box;
  }

  private openViewer(url: string, label: string): void {
    this.viewer.replaceChildren(h('img', { src: url, alt: label, class: 'history-full' }));
    this.viewer.hidden = false;
  }

  private closeViewer(): void {
    this.viewer.hidden = true;
    this.viewer.replaceChildren();
  }

  private scheduleReload(): void {
    if (this.reloadTimer) clearTimeout(this.reloadTimer);
    const day = this.daySelect.value;
    this.reloadTimer = setTimeout(() => {
      this.reloadTimer = null;
      void this.loadDay(day, DELETED);
    }, this.deps.reloadDelayMs ?? 2000);
  }
}
