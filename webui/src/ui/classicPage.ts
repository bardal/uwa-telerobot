// The 1996-98 CGI interface (NEWROBOT/TEMPLATE: OPERATOR/OPRTR.HTM, OBSERVER,
// CLICKED) over the same gateway session as the applet page. Like the CGI
// (Telerobot.cpp), each press of "Move the robot" makes one move and then the
// page comes back with fresh still photos; nothing updates in between. Clicking
// one camera image, then another, triangulates a point and moves there
// (ProcessAnyImageClicks).

import { Camera, triangulate, type UV, type Vec3 } from '../geometry/camera';
import type { In, Out } from '../net/protocol';
import { stateAsGBHString } from '../pathscript';
import { h } from './dom';

type ImageHeader = Extract<Out, { t: 'image' }>;
type RobotStateMsg = Extract<Out, { t: 'robotState' }>;
type Mode = 'operator' | 'observer' | 'clicked';

/** How long each step may take before the page comes back anyway. */
export const TIMEOUTS = { control: 5000, move: 90_000, archive: 8000, photo: 6000 };
/** After a rate-limited camera request: the gateway refills one a second. */
export const RATE_RETRY_MS = 1100;

export const HEADINGS: Record<Mode, string> = {
  operator: 'Controller page for the telerobot',
  observer: 'Observer page for the telerobot',
  clicked: 'Controller page for the telerobot',
};

export interface ClassicPageDeps {
  send: (m: In) => void;
  /** The 2000 applet interface. */
  showApplet: () => void;
  openHistory: () => void;
  logout: () => void;
  now?: () => number;
  objectUrl?: { create(b: Blob): string; revoke(url: string): void };
  timeouts?: Partial<typeof TIMEOUTS>;
}

interface Photo {
  url: string;
  width: number;
  height: number;
  calibration: number[];
}

interface CameraCell {
  name: string;
  img: HTMLImageElement;
  off: HTMLInputElement;
  photo: Photo | null;
}

type Waiter = { pick: (m: Out) => boolean; done: (m: Out | null) => void };

const FIELDS = ['X', 'Y', 'Z', 'Spin', 'Tilt'] as const;
type Field = (typeof FIELDS)[number];

const time = (t: number) => new Date(t).toLocaleTimeString('en-GB');

export class ClassicPage {
  readonly el: HTMLElement;
  readonly heading: HTMLElement;
  readonly notice: HTMLElement;
  readonly error: HTMLElement;
  readonly status: HTMLElement;
  readonly stats: HTMLElement;
  readonly fields: Record<Field, HTMLInputElement>;
  readonly gripper: HTMLInputElement;
  readonly relative: HTMLInputElement;
  readonly moveButton: HTMLButtonElement;
  readonly controlButton: HTMLButtonElement;
  private readonly photos: HTMLElement;
  private cells: CameraCell[] = [];
  private clicks = new Map<string, UV>();
  private waiters: Waiter[] = [];
  private ourNick: string | null = null;
  private controllerNick: string | null = null;
  private state: RobotStateMsg | null = null;
  private moveCount = 0;
  private readonly onSince: number;
  private lastMove: number | null = null;
  private busy = false;
  private shown = false;
  private disposed = false;
  private readonly now: () => number;
  private readonly t: typeof TIMEOUTS;
  private readonly urls: { create(b: Blob): string; revoke(url: string): void };

  constructor(private readonly deps: ClassicPageDeps) {
    this.now = deps.now ?? Date.now;
    this.t = { ...TIMEOUTS, ...deps.timeouts };
    this.urls = deps.objectUrl ?? { create: (b) => URL.createObjectURL(b), revoke: (u) => URL.revokeObjectURL(u) };
    this.onSince = this.now();

    this.heading = h('h1', { text: HEADINGS.operator });
    this.notice = h('p', { class: 'classic-notice' });
    this.photos = h('div', { class: 'classic-photos' });
    this.error = h('p', { class: 'classic-error', role: 'alert' });
    this.status = h('p', { class: 'classic-status', role: 'status' });
    const field = (name: Field) => h('input', { type: 'text', size: 4, maxlength: 6, name, 'aria-label': name });
    this.fields = { X: field('X'), Y: field('Y'), Z: field('Z'), Spin: field('Spin'), Tilt: field('Tilt') };
    this.gripper = h('input', { type: 'checkbox', name: 'Gripper' });
    this.relative = h('input', { type: 'checkbox', name: 'MoveType' });
    this.moveButton = h('button', { type: 'submit', text: ' Move the robot ' });
    this.controlButton = h('button', { type: 'button', text: 'Try to get control', hidden: true });
    this.controlButton.addEventListener('click', () => void this.tryControl());
    const label = (text: string, input: HTMLInputElement) => h('label', {}, `${text} `, input);
    const form = h(
      'form',
      { class: 'classic-form' },
      this.photos,
      h('hr'),
      this.error,
      h(
        'table',
        {},
        h('tr', {}, h('td', {}, label('X position', this.fields.X)), h('td', {}, label('Y position', this.fields.Y)), h('td', {}, label('Z position', this.fields.Z))),
        h('tr', {}, h('td', {}, label('Spin', this.fields.Spin)), h('td', {}, label('Tilt', this.fields.Tilt))),
        h('tr', {}, h('td', {}, label('Gripper open', this.gripper)), h('td', {}, label('Move relative', this.relative))),
      ),
      h('p', {}, this.moveButton, ' ', this.controlButton),
      this.status,
    );
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      void this.submit();
    });
    this.stats = h('p');
    const link = (text: string, go: () => void) => {
      const a = h('a', { href: '#', text });
      a.addEventListener('click', (e) => {
        e.preventDefault();
        go();
      });
      return a;
    };
    this.el = h(
      'div',
      { class: 'page classic-page' },
      this.heading,
      this.notice,
      form,
      h(
        'p',
        {},
        'The robot positions are in mm and angles in degrees. X increases to the right and Y away from ' +
          'the cameras; Z goes up from the table. The images are calibrated: click a point in one image, ' +
          'then the same point in another, to send the robot there. To stand a block up, pick it up with a ' +
          'tilt of -45, then go up and rotate to a tilt of +45. If you want a faster response turn some of the cameras off.',
      ),
      h('p', {}, 'You have exclusive control if you use the robot within 3 minutes.'),
      this.stats,
      h(
        'ul',
        {},
        h('li', {}, link('Use the 2000 Java applet interface', () => deps.showApplet())),
        h('li', {}, link('History: moves other people have made', () => deps.openHistory())),
        h('li', {}, h('a', { href: '#/about', text: 'About the UWA Telerobot' })),
        h('li', {}, link('Log out', () => deps.logout())),
      ),
      h('hr'),
      h(
        'p',
        { class: 'classic-footer' },
        'A re-creation of the UWA Telerobot’s 1996 forms interface (the telerobt.exe CGI), driving the same simulated robot.',
      ),
    );
    this.render();
  }

  // ---- gateway messages ----

  handle(m: Out): void {
    switch (m.t) {
      case 'welcome':
        this.ourNick = m.nick;
        this.setCameras(m.cameras);
        if (this.shown) void this.refreshPhotos();
        break;
      case 'robotState':
        this.state = { ...m };
        if (!this.busy && !this.fields.X.value) this.fillFromState();
        break;
      case 'controller':
        this.controllerNick = m.nick;
        if (!this.busy) this.render();
        break;
      default:
        break;
    }
    for (const w of [...this.waiters]) {
      if (w.pick(m)) {
        this.waiters.splice(this.waiters.indexOf(w), 1);
        w.done(m);
      }
    }
  }

  /** A camera frame: kept only while that camera's photo is wanted. */
  image(header: ImageHeader, jpeg: Blob): void {
    if (header.calibration.length !== 12) return;
    const want = this.waiters.find((w) => w.pick({ ...header }));
    if (!want) return;
    const cell = this.cells.find((c) => c.name === header.camera);
    if (!cell) return;
    if (cell.photo) this.urls.revoke(cell.photo.url);
    cell.photo = { url: this.urls.create(jpeg), width: header.width, height: header.height, calibration: header.calibration };
    this.waiters.splice(this.waiters.indexOf(want), 1);
    want.done(header);
  }

  /** The page came into view: like loading the CGI's page, it fetches the latest photos. */
  show(): void {
    this.shown = true;
    if (this.state) this.fillFromState();
    void this.refreshPhotos();
  }

  hide(): void {
    this.shown = false;
  }

  disconnected(): void {
    this.controllerNick = null;
    for (const w of this.waiters.splice(0)) w.done(null);
  }

  dispose(): void {
    this.disposed = true;
    this.disconnected();
    for (const c of this.cells) if (c.photo) this.urls.revoke(c.photo.url);
  }

  mode(): Mode {
    if (this.controllerNick !== null && this.controllerNick !== this.ourNick) return 'observer';
    return this.clicks.size === 1 ? 'clicked' : 'operator';
  }

  // ---- the form ----

  /** "Move the robot": take control if nobody has it, make one move, then fresh photos. */
  async submit(target?: Vec3): Promise<void> {
    if (this.busy) return;
    const pose = target ? this.withPoint(target) : this.readForm();
    if (typeof pose === 'string') {
      this.error.textContent = pose;
      return;
    }
    this.error.textContent = '';
    this.setBusy(true, 'Moving the robot… the page comes back when the move is done.');
    const pending = this.waiters.length;
    try {
      if (!(await this.ensureControl())) return;
      const script = stateAsGBHString({ pose, gripperOpen: this.gripper.checked });
      const ended = this.waitFor((m) => m.t === 'commandStatus' && m.script === script && (m.state === 'done' || m.state === 'error'), this.t.move);
      const archived = this.waitFor((m) => m.t === 'archived' && m.kind === 'move', this.t.move + this.t.archive);
      const refused = this.waitFor((m) => m.t === 'error' && m.code !== 'lab-unavailable', this.t.move);
      this.deps.send({ t: 'command', script });
      const first = await Promise.race([ended, refused]);
      if (!first) {
        this.error.textContent = 'The robot did not answer.';
      } else if (first.t === 'error') {
        this.error.textContent = first.message;
      } else if (first.t === 'commandStatus') {
        this.moveCount++;
        this.lastMove = this.now();
        if (first.state === 'error') this.error.textContent = first.text || 'The robot could not make that move.';
        // the gateway photographs every camera once the arm settles, then archives the move
        let late: ReturnType<typeof setTimeout> | undefined;
        await Promise.race([archived, new Promise((r) => (late = setTimeout(r, this.t.archive)))]);
        clearTimeout(late);
      }
      this.dropWaitersFrom(pending);
      this.fillFromState();
      // back to the top, as the CGI's reloaded page was, to see the new photos come in
      // (on a phone the button is three photos down); an error stays in view by the form
      if (!this.error.textContent) this.el.scrollIntoView?.({ block: 'start' });
      await this.refreshPhotos();
    } finally {
      this.dropWaitersFrom(pending);
      this.setBusy(false, '');
    }
  }

  /** Ends the waits started since there were `n` (a move's leftovers: archived, refused). */
  private dropWaitersFrom(n: number): void {
    for (const w of this.waiters.splice(n)) w.done(null);
  }

  private async tryControl(): Promise<void> {
    if (this.busy) return;
    this.setBusy(true, 'Trying to get control…');
    try {
      await this.ensureControl(true);
      await this.refreshPhotos();
    } finally {
      this.setBusy(false, '');
    }
  }

  /** true once we hold control; the CGI handed it to whoever came along when it was free. */
  private async ensureControl(force = false): Promise<boolean> {
    if (this.ourNick !== null && this.controllerNick === this.ourNick) return true;
    if (!force && this.controllerNick !== null) {
      this.error.textContent = `Sorry, the robot is currently being used by ${this.controllerNick}.`;
      return false;
    }
    const got = this.waitFor((m) => (m.t === 'controller' && m.nick === this.ourNick) || (m.t === 'error' && m.code === 'not-in-control'), this.t.control);
    this.deps.send({ t: 'takeControl' });
    const m = await got;
    if (m?.t === 'controller') return true;
    this.error.textContent = m?.t === 'error' ? m.message : `Sorry, the robot is currently being used by ${this.controllerNick ?? 'someone else'}.`;
    return false;
  }

  private readForm(): { x: number; y: number; z: number; spin: number; tilt: number } | string {
    const v: Partial<Record<Field, number>> = {};
    for (const f of FIELDS) {
      const s = this.fields[f].value.trim();
      const n = Number(s);
      if (s === '' || !Number.isFinite(n)) return `${f} must be a number.`;
      v[f] = n;
    }
    const base = this.relative.checked ? this.state : null;
    if (this.relative.checked && !base) return 'The robot’s position is not known yet; untick Move relative.';
    return {
      x: v.X! + (base?.x ?? 0),
      y: v.Y! + (base?.y ?? 0),
      z: v.Z! + (base?.z ?? 0),
      spin: v.Spin! + (base?.spin ?? 0),
      tilt: v.Tilt! + (base?.tilt ?? 0),
    };
  }

  /** A triangulated point with the form's spin and tilt (absolute, whatever Move relative says). */
  private withPoint(p: Vec3): { x: number; y: number; z: number; spin: number; tilt: number } | string {
    const spin = Number(this.fields.Spin.value);
    const tilt = Number(this.fields.Tilt.value);
    if (!Number.isFinite(spin) || !Number.isFinite(tilt)) return 'Spin and Tilt must be numbers.';
    return { x: p.x, y: p.y, z: p.z, spin, tilt };
  }

  private fillFromState(): void {
    const s = this.state;
    if (!s) return;
    const r = (n: number) => String(Math.round(n));
    this.fields.X.value = r(s.x);
    this.fields.Y.value = r(s.y);
    this.fields.Z.value = r(s.z);
    this.fields.Spin.value = r(s.spin);
    this.fields.Tilt.value = r(s.tilt);
    this.gripper.checked = s.gripperOpen;
    this.relative.checked = false;
  }

  // ---- the photos ----

  private setCameras(names: string[]): void {
    for (const c of this.cells) if (c.photo) this.urls.revoke(c.photo.url);
    this.clicks.clear();
    this.cells = names.map((name) => {
      const img = h('img', { alt: name, class: 'classic-photo' });
      img.addEventListener('click', (e) => this.clicked(name, img, e));
      const off = h('input', { type: 'checkbox', name: `${name}DontRefresh` });
      return { name, img, off, photo: null };
    });
    this.photos.replaceChildren(
      ...this.cells.map((c) =>
        h('figure', { class: 'classic-camera' }, c.img, h('figcaption', {}, c.name, h('br'), h('label', {}, 'Camera Off ', c.off))),
      ),
    );
    this.render();
  }

  /**
   * Each camera that is not off, one at a time: selecting a camera sends its
   * latest photo; one that has none yet is asked to take one. Each photo shows
   * as it arrives, as the images of a 1996 page did. The gateway allows camera
   * requests in a burst of 3, then 1 a second: a refused select waits and retries.
   */
  private async refreshPhotos(): Promise<void> {
    for (const c of this.cells) {
      if (this.disposed) return;
      if (c.off.checked) continue;
      const ask = (m: In) => {
        const got = this.waitFor((o) => (o.t === 'image' && o.camera === c.name) || (o.t === 'error' && o.code === 'rate-limited'), this.t.photo);
        this.deps.send(m);
        return got;
      };
      const select: In = { t: 'selectCamera', camera: c.name };
      let r = await ask(select);
      for (let i = 0; i < 2 && r?.t === 'error' && !this.disposed; i++) {
        await new Promise((ok) => setTimeout(ok, RATE_RETRY_MS));
        r = await ask(select);
      }
      // selected, but it has no photo yet
      if (r === null && !this.disposed) await ask({ t: 'newImage', camera: c.name });
      this.render();
    }
  }

  private clicked(name: string, img: HTMLImageElement, e: MouseEvent): void {
    const cell = this.cells.find((c) => c.name === name);
    if (!cell?.photo || this.busy || this.mode() === 'observer') return;
    const r = img.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) return;
    const uv = { u: ((e.clientX - r.left) * cell.photo.width) / r.width, v: ((e.clientY - r.top) * cell.photo.height) / r.height };
    this.clicks.set(name, uv);
    if (this.clicks.size < 2) {
      this.render();
      return;
    }
    const rays = [...this.clicks].map(([n, p]) => new Camera(this.cells.find((c) => c.name === n)!.photo!.calibration).uv2line(p));
    this.clicks.clear();
    const point = triangulate(rays[0]!, rays[1]!);
    if (!point) {
      this.error.textContent = 'Those two clicks do not meet at a point; try again.';
      this.render();
      return;
    }
    void this.submit(point);
  }

  // ---- rendering ----

  private render(): void {
    const mode = this.mode();
    this.heading.textContent = HEADINGS[mode];
    this.notice.textContent =
      mode === 'observer'
        ? `Sorry the robot is currently being used by ${this.controllerNick}; you may try and gain control of it by hitting the button below. The latest photos are:`
        : mode === 'clicked'
          ? 'You clicked an image, now click the other image to define a point in space for the robot to go to.'
          : '';
    this.controlButton.hidden = mode !== 'observer';
    this.moveButton.disabled = this.busy || mode === 'observer';
    for (const f of [...FIELDS.map((k) => this.fields[k]), this.gripper, this.relative]) f.disabled = this.busy || mode === 'observer';
    for (const c of this.cells) {
      if (c.off.checked || !c.photo) {
        c.img.removeAttribute('src');
        c.img.classList.add('classic-photo-off');
      } else {
        c.img.src = c.photo.url;
        c.img.width = c.photo.width;
        c.img.height = c.photo.height;
        c.img.classList.remove('classic-photo-off');
      }
      c.img.classList.toggle('classic-photo-clicked', this.clicks.has(c.name));
    }
    this.stats.textContent =
      `You have made ${this.moveCount} move${this.moveCount === 1 ? '' : 's'}, and have been on since ${time(this.onSince)}.` +
      (this.lastMove === null ? '' : ` Your last move was made at ${time(this.lastMove)}.`);
  }

  private setBusy(b: boolean, text: string): void {
    this.busy = b;
    this.status.textContent = text;
    this.el.classList.toggle('classic-busy', b);
    this.render();
  }

  private waitFor(pick: (m: Out) => boolean, ms: number): Promise<Out | null> {
    return new Promise((resolve) => {
      const w: Waiter = {
        pick,
        done: (m) => {
          clearTimeout(timer);
          resolve(m);
        },
      };
      const timer = setTimeout(() => {
        const i = this.waiters.indexOf(w);
        if (i >= 0) this.waiters.splice(i, 1);
        resolve(null);
      }, ms);
      this.waiters.push(w);
    });
  }
}
