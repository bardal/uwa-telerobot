// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { startApp, type AppDeps, type GatewayLike } from '../src/app';
import type { Config } from '../src/config';
import type { GatewayOpts } from '../src/net/gateway';
import type { In, Out } from '../src/net/protocol';
import type { WakeState } from '../src/net/wake';
import { CAPPED } from '../src/ui/wakingPage';

const ctxStub = new Proxy({}, { get: () => () => undefined, set: () => true }) as unknown as CanvasRenderingContext2D;

class FakeGateway implements GatewayLike {
  static all: FakeGateway[] = [];
  handlers = new Map<string, ((m: Out) => void)[]>();
  sent: In[] = [];
  closed = false;
  reconnects = 0;
  constructor(
    readonly url: string,
    readonly hello: () => Promise<In & { t: 'hello' }>,
    readonly opts: GatewayOpts,
  ) {
    FakeGateway.all.push(this);
  }
  on<T extends Out['t']>(t: T, fn: (m: Extract<Out, { t: T }>) => void): void {
    const l = this.handlers.get(t) ?? [];
    l.push(fn as (m: Out) => void);
    this.handlers.set(t, l);
  }
  onImage(): void {}
  send(m: In): void {
    this.sent.push(m);
  }
  close(): void {
    this.closed = true;
  }
  reconnect(): void {
    this.reconnects++;
  }
  emit(m: Out): void {
    for (const fn of this.handlers.get(m.t) ?? []) fn(m);
  }
}

const config: Config = { region: 'eu-west-2', userPoolDomain: '', clientId: '', turnstileSiteKey: 'k', redirectUri: '' };
const welcome = (nick: string): Out => ({
  t: 'welcome',
  nick,
  level: 'guest',
  cameras: ['Colour', 'YCam', 'ZCam'],
  commit: 'c',
  limits: { script: 400, waypoints: 30, chat: 300 },
});

const flush = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve();
};

let wakeResult: WakeState = { kind: 'running' };

function deps(extra: Partial<AppDeps> = {}): AppDeps {
  const store = new Map<string, string>();
  return {
    config: async () => config,
    auth: { signIn: vi.fn(async () => undefined), handleRedirect: async () => false, idToken: () => null, signOut: vi.fn(async () => undefined) },
    storage: () => ({ getItem: (k: string) => store.get(k) ?? null, setItem: (k: string, v: string) => void store.set(k, v) }) as unknown as Storage,
    turnstileToken: async () => 'tok',
    makeGateway: (u, hl, o) => new FakeGateway(u, hl, o),
    ensureAwake: async (getProof, onProgress) => {
      await getProof();
      onProgress(wakeResult);
      return wakeResult;
    },
    ...extra,
  };
}

const guestButton = (root: HTMLElement) => [...root.querySelectorAll('button')].find((b) => b.textContent === 'Guest')!;

async function loggedIn(nick = 'ann') {
  const root = document.createElement('div');
  document.body.appendChild(root);
  const app = startApp(root, deps());
  await app.ready;
  app.login.nicknameField.value = nick;
  guestButton(root).click();
  await flush();
  const g = FakeGateway.all.at(-1)!;
  return { root, app, g };
}

beforeEach(() => {
  FakeGateway.all = [];
  wakeResult = { kind: 'running' };
  document.body.replaceChildren();
  globalThis.location.hash = '';
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctxStub as never);
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} unobserve() {} });
});

describe('app flow', () => {
  it('Guest: Turnstile, wake, hello, then the control page with the real nick', async () => {
    const { app, g } = await loggedIn('ann');
    expect(app.view()).toBe('waking');
    expect(await g.hello()).toEqual({ t: 'hello', nick: 'ann', turnstile: 'tok' });
    g.emit(welcome('ann~2'));
    expect(app.view()).toBe('control');
    expect(app.control()!.nick()).toBe('ann~2');
    expect(g.sent).toContainEqual({ t: 'selectCamera', camera: 'Colour' });
  });

  it('shows the cap message and does not connect', async () => {
    wakeResult = { kind: 'capped' };
    const { app } = await loggedIn();
    expect(app.view()).toBe('waking');
    expect(app.waking.heading.textContent).toBe(CAPPED);
    expect(FakeGateway.all).toHaveLength(0);
  });

  it('a lost connection shows the waking page until the next welcome, which restores the camera', async () => {
    const { app, g } = await loggedIn();
    g.emit(welcome('ann'));
    (app.control()!.el.querySelectorAll('.camera-buttons button')[2] as HTMLButtonElement).click();
    g.opts.onClose!();
    expect(app.view()).toBe('waking');
    g.sent.length = 0;
    g.emit(welcome('ann'));
    expect(app.view()).toBe('control');
    expect(g.sent).toEqual([{ t: 'selectCamera', camera: 'ZCam' }]);
  });

  it('onWakeNeeded runs the wake flow again and reconnects', async () => {
    const { g } = await loggedIn();
    g.emit(welcome('ann'));
    g.opts.onWakeNeeded!();
    await flush();
    expect(g.reconnects).toBe(1);
  });

  it('lab stopping goes to the waking page with Reconnect', async () => {
    const { app, g } = await loggedIn();
    g.emit(welcome('ann'));
    g.emit({ t: 'lab', state: 'stopping', message: 'Closed after 10 minutes without input' });
    expect(g.closed).toBe(true);
    expect(app.view()).toBe('waking');
    expect(app.waking.heading.textContent).toBe('Closed after 10 minutes without input');
    expect(app.waking.retry.textContent).toBe('Reconnect');
    const page = app.control();
    app.waking.retry.click();
    await flush();
    const g2 = FakeGateway.all.at(-1)!;
    expect(g2).not.toBe(g);
    g2.emit(welcome('ann'));
    expect(app.control()).toBe(page);
    expect(app.view()).toBe('control');
  });

  it('a refused hello returns to the login page with the reason', async () => {
    const { app, g } = await loggedIn();
    g.emit({ t: 'error', code: 'unauthorised', message: 'Turnstile check failed' });
    expect(g.closed).toBe(true);
    expect(app.view()).toBe('login');
    expect(app.login.console.text()).toContain('Turnstile check failed');
  });

  it('Logout returns to the login page', async () => {
    const { app, g } = await loggedIn();
    g.emit(welcome('ann'));
    [...app.control()!.el.querySelectorAll('.menu-item')].find((x) => x.textContent?.includes('Logout'))!.dispatchEvent(new Event('click'));
    [...document.querySelectorAll('.awt-dialog button')].find((b) => b.textContent === 'Yes')!.dispatchEvent(new Event('click'));
    await flush();
    expect(g.closed).toBe(true);
    expect(app.view()).toBe('login');
  });

  it('routes #/history to the History page', async () => {
    const root = document.createElement('div');
    const app = startApp(root, deps());
    await app.ready;
    globalThis.location.hash = '#/history';
    globalThis.dispatchEvent(new Event('hashchange'));
    expect(app.view()).toBe('history');
    expect(root.querySelector('.history-page')).not.toBeNull();
  });

  it('dev sign-in sends hello.dev', async () => {
    const root = document.createElement('div');
    const app = startApp(root, deps({ config: async () => ({ ...config, dev: true }) }));
    await app.ready;
    app.login.nicknameField.value = 'dee';
    (root.querySelector('input[aria-label="admin"]') as HTMLInputElement).checked = true;
    [...root.querySelectorAll('button')].find((b) => b.textContent === 'Dev sign in')!.click();
    await flush();
    expect(await FakeGateway.all.at(-1)!.hello()).toEqual({ t: 'hello', nick: 'dee', dev: { sub: 'dev-dee', groups: ['admin'] } });
  });
});

describe('review fixes', () => {
  it('a de-duplicated welcome nick is shown, but later hellos ask for the requested nick', async () => {
    const { app, g } = await loggedIn('ann');
    g.emit(welcome('ann~2'));
    expect(app.control()!.nick()).toBe('ann~2');
    g.opts.onClose!();
    expect(await g.hello()).toEqual({ t: 'hello', nick: 'ann', turnstile: 'tok' });
  });

  it('the dev sub stays dev-<requested nick> after a de-duplicated welcome', async () => {
    const root = document.createElement('div');
    const app = startApp(root, deps({ config: async () => ({ ...config, dev: true }) }));
    await app.ready;
    app.login.nicknameField.value = 'dee';
    [...root.querySelectorAll('button')].find((b) => b.textContent === 'Dev sign in')!.click();
    await flush();
    const g = FakeGateway.all.at(-1)!;
    g.emit(welcome('dee~2'));
    expect(await g.hello()).toEqual({ t: 'hello', nick: 'dee', dev: { sub: 'dev-dee', groups: [] } });
  });

  it('a config load error stays visible, a typed nick survives, and Guest waits for the config', async () => {
    let release: (c: Config) => void = () => undefined;
    const root = document.createElement('div');
    const app = startApp(root, deps({ config: () => new Promise<Config>((r) => { release = r; }) }));
    app.login.nicknameField.value = 'early';
    expect(guestButton(root).disabled).toBe(true);
    release(config);
    await app.ready;
    expect(app.login.nicknameField.value).toBe('early');
    expect(guestButton(root).disabled).toBe(false);

    const root2 = document.createElement('div');
    const app2 = startApp(root2, deps({ config: async () => { throw new Error('HTTP 503'); } }));
    await app2.ready;
    expect(app2.login.console.text()).toContain('Could not load the configuration: HTTP 503');
  });

  it('the waking page offers Back to login when capped or errored', async () => {
    wakeResult = { kind: 'capped' };
    const { app } = await loggedIn();
    const back = app.waking.back;
    expect(back.hidden).toBe(false);
    back.click();
    expect(app.view()).toBe('login');
    expect(guestButton(app.root).disabled).toBe(false);
  });

  it('Sign in is guarded against a double click', async () => {
    let finish: () => void = () => undefined;
    const signIn = vi.fn(() => new Promise<void>((r) => { finish = r; }));
    const root = document.createElement('div');
    const d = deps({ config: async () => ({ ...config, userPoolDomain: 'pool.example', clientId: 'cid' }) });
    d.auth = { ...d.auth!, signIn };
    const app = startApp(root, d);
    await app.ready;
    app.login.nicknameField.value = 'ann';
    const b = [...root.querySelectorAll('button')].find((x) => x.textContent === 'Sign in')!;
    b.click();
    b.click();
    expect(signIn).toHaveBeenCalledTimes(1);
    finish();
  });
});

describe('e2e hook', () => {
  it('is absent without ?e2e=1', () => {
    startApp(document.createElement('div'), deps());
    expect((globalThis as { __telerobot?: unknown }).__telerobot).toBeUndefined();
  });

  it('with ?e2e=1 is a read-only set of getters', async () => {
    globalThis.history.replaceState(null, '', '/?e2e=1');
    const { g } = await loggedIn('ann');
    g.emit(welcome('ann'));
    g.emit({ t: 'controller', nick: 'ann' });
    g.emit({ t: 'robotState', x: 1, y: 2, z: 3, spin: -90, tilt: 0, gripperOpen: true, moving: false });
    const api = (globalThis as unknown as { __telerobot: Record<string, (...a: string[]) => unknown> }).__telerobot;
    expect(Object.keys(api).sort()).toEqual(['cameraMatrix', 'canvasRect', 'controller', 'cursorPose', 'nick', 'robotState']);
    expect(api.nick!()).toBe('ann');
    expect(api.controller!()).toBe('ann');
    expect(api.robotState!()).toMatchObject({ x: 1, y: 2, z: 3 });
    expect(api.cursorPose!()).toEqual({ x: 250, y: 250, z: 100, spin: -90, tilt: 0 });
    expect(api.cameraMatrix!('Colour')).toBeNull();
    expect(api.canvasRect!('Colour')).toMatchObject({ width: expect.any(Number) });
    expect(Object.isFrozen(api)).toBe(true);
    expect(() => { (globalThis as { __telerobot?: unknown }).__telerobot = {}; }).toThrow();
    globalThis.history.replaceState(null, '', '/');
  });
});
