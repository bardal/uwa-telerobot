// The app: routes (#/ is the landing page before a session and control in one,
// #/about the landing page, #/login the login page, #/classic the 1996 forms interface
// over the same session, #/history is History), the waking page while the lab
// starts, and the session flow login -> wake -> gateway.

import { loadConfig, type Config } from './config';
import { HistoryPage } from './archive/history';
import { auth as defaultAuth } from './net/auth';
import { buildHello, Gateway, type GatewayOpts } from './net/gateway';
import type { In, Out } from './net/protocol';
import { TurnstileWidget } from './net/turnstile';
import { ensureAwake, type WakeState } from './net/wake';
import { ClassicPage } from './ui/classicPage';
import { ControlPage } from './ui/controlPage';
import { h } from './ui/dom';
import { APPLET_HASH, FORMS_HASH, LandingPage } from './ui/landingPage';
import { LoginPage } from './ui/loginPage';
import { WakingPage } from './ui/wakingPage';

const NICK_KEY = 'telerobot.nick';
/** The way in (#/login or #/classic) chosen before a sign-in redirect. */
const AFTER_SIGN_IN = 'telerobot.afterSignIn';
const OUT_TYPES: Out['t'][] = [
  'welcome', 'robotState', 'controller', 'commandStatus', 'users', 'chat', 'workspace',
  'cameraSettings', 'archived', 'lab', 'console', 'error',
];

type Mode = { kind: 'guest' } | { kind: 'signedIn' } | { kind: 'dev'; admin: boolean };
type Proof = { turnstile?: string; idToken?: string };
type Auth = Pick<typeof defaultAuth, 'signIn' | 'handleRedirect' | 'idToken' | 'signOut'>;

export interface GatewayLike {
  on<T extends Out['t']>(t: T, fn: (m: Extract<Out, { t: T }>) => void): void;
  onImage(fn: (h: Extract<Out, { t: 'image' }>, jpeg: Blob) => void): void;
  send(m: In): void;
  close(): void;
  reconnect(): void;
}

export interface AppDeps {
  config?: () => Promise<Config>;
  auth?: Auth;
  fetchImpl?: typeof fetch;
  storage?: () => Storage | null;
  /** Fresh Turnstile token (single use). */
  turnstileToken?: (container: HTMLElement, siteKey: string) => Promise<string>;
  makeGateway?: (url: string, hello: () => Promise<In & { t: 'hello' }>, opts: GatewayOpts) => GatewayLike;
  ensureAwake?: (getProof: () => Promise<Proof>, onProgress: (s: WakeState) => void) => Promise<WakeState>;
  openWindow?: (url: string, name: string) => void;
}

function storage(deps: AppDeps): Storage | null {
  try {
    return deps.storage ? deps.storage() : sessionStorage;
  } catch {
    return null;
  }
}

function wsUrl(): string {
  const l = globalThis.location;
  return `${l.protocol === 'https:' ? 'wss' : 'ws'}://${l.host}/ws`;
}

export interface App {
  readonly root: HTMLElement;
  view(): 'landing' | 'login' | 'waking' | 'control' | 'classic' | 'history';
  control(): ControlPage | null;
  classic(): ClassicPage | null;
  readonly login: LoginPage;
  waking: WakingPage;
  ready: Promise<void>;
}

export function startApp(root: HTMLElement, deps: AppDeps = {}): App {
  const fetchImpl = deps.fetchImpl ?? ((...a: Parameters<typeof fetch>) => fetch(...a));
  const auth = deps.auth ?? defaultAuth;
  let config: Config = { region: '', userPoolDomain: '', clientId: '', turnstileSiteKey: '', redirectUri: '' };

  const store = {
    get(): string {
      try { return storage(deps)?.getItem(NICK_KEY) ?? ''; } catch { return ''; }
    },
    set(n: string): void {
      try { storage(deps)?.setItem(NICK_KEY, n); } catch { /* unavailable */ }
    },
  };

  // The Turnstile widget lives in a host that stays attached whichever page shows,
  // because every reconnect needs a fresh token.
  const turnstileHost = h('div', { class: 'turnstile-host' });
  let widget: TurnstileWidget | null = null;
  const turnstileToken =
    deps.turnstileToken ??
    ((container: HTMLElement, siteKey: string) => {
      widget ??= new TurnstileWidget({ siteKey, container });
      return widget.token();
    });

  const viewHost = h('div', { class: 'view-host' });
  root.replaceChildren(viewHost, turnstileHost);

  let view: 'login' | 'waking' | 'control' = 'login';
  let mode: Mode | null = null;
  /** The nick the user asked for. Every hello (and the dev sub) uses it; the gateway's
   * de-duplicated one ("ann~2") is only shown, and matched against the controller. */
  let nick = store.get();
  let gateway: GatewayLike | null = null;
  let control: ControlPage | null = null;
  let classic: ClassicPage | null = null;
  let welcomed = false;
  /** An "unauthorised" error that was the last thing before the socket closed: a kick or a ban. */
  let refusal: string | null = null;
  let wakeRun = 0;

  const waking = new WakingPage(
    () => void wakeThenConnect(),
    () => endSession(''),
    Date.now,
    Math.random,
    () => goHome(),
  );

  /** Leaves any session (or wake) for the landing page. */
  function goHome(): void {
    endSession('');
    if (globalThis.location) globalThis.location.hash = '#/';
    render();
  }
  let login = makeLogin(false, false);
  login.setBusy(true); // until the config (site key, user pool) is loaded
  let admin = false; // the current session's welcome.level === 'admin'
  const landing = new LandingPage();
  const history = new HistoryPage({
    fetchImpl,
    canDelete: () => admin && welcomed && gateway !== null,
    send: (m) => gateway?.send(m),
  });
  let historyShown = false;

  function makeLogin(signInAvailable: boolean, dev: boolean): LoginPage {
    return new LoginPage({
      nick,
      signInAvailable,
      dev,
      onGuest: (n) => begin(n, { kind: 'guest' }),
      onSignIn: (n) => void signIn(n),
      onDev: (n, admin) => begin(n, { kind: 'dev', admin }),
    });
  }

  function route(): 'landing' | 'login' | 'waking' | 'control' | 'classic' | 'history' {
    const hash = globalThis.location?.hash ?? '';
    if (hash === '#/history') return 'history';
    if (hash === '#/about') return 'landing';
    // before a session the site opens on the landing page; its two ways in (#/login, #/classic) log in first
    if (view === 'login' && hash !== APPLET_HASH && hash !== FORMS_HASH) return 'landing';
    // the forms interface is another view of a session: only once there is one
    return hash === '#/classic' && view === 'control' && classic ? 'classic' : view;
  }

  let classicShown = false;
  function render(): void {
    const r = route();
    if (r === 'history' && !historyShown) void history.load();
    historyShown = r === 'history';
    if ((r === 'classic') !== classicShown) {
      classicShown = r === 'classic';
      if (classicShown) classic?.show();
      else {
        classic?.hide();
        // the forms page selected each camera in turn: give the applet page its own back
        const cam = control?.selectedCamera();
        if (cam && welcomed) gateway?.send({ t: 'selectCamera', camera: cam });
      }
    }
    const el =
      r === 'history' ? history.el
      : r === 'landing' ? landing.el
      : r === 'login' ? login.el
      : r === 'waking' ? waking.el
      : r === 'classic' ? classic!.el
      : (control?.el ?? login.el);
    if (viewHost.firstChild !== el) viewHost.replaceChildren(el);
  }

  function setView(v: typeof view): void {
    view = v;
    if (v !== 'waking') waking.stopTimer();
    render();
  }

  globalThis.addEventListener?.('hashchange', render);

  async function getProof(): Promise<Proof> {
    if (mode?.kind === 'signedIn') {
      const t = auth.idToken();
      if (!t) throw new Error('Your sign-in has expired');
      return { idToken: t };
    }
    return { turnstile: await turnstileToken(turnstileHost, config.turnstileSiteKey) };
  }

  async function hello(): Promise<In & { t: 'hello' }> {
    const m = mode;
    if (m?.kind === 'dev') {
      const devIdentity = { sub: `dev-${nick}`, groups: m.admin ? ['admin'] : [] };
      return buildHello({ nick, proof: {}, config, devIdentity });
    }
    if (m?.kind === 'signedIn' && !auth.idToken()) {
      queueMicrotask(() => endSession('Your sign-in has expired; please sign in again'));
      throw new Error('signed out');
    }
    return buildHello({ nick, proof: await getProof(), config });
  }

  function begin(n: string, m: Mode): void {
    nick = n;
    mode = m;
    store.set(n);
    login.setBusy(true);
    void wakeThenConnect();
  }

  async function signIn(n: string): Promise<void> {
    nick = n;
    store.set(n);
    if (auth.idToken()) {
      begin(n, { kind: 'signedIn' });
      return;
    }
    login.setBusy(true); // one redirect, however many clicks
    // the redirect back lands on the bare site: remember which interface was chosen
    try { globalThis.sessionStorage?.setItem(AFTER_SIGN_IN, globalThis.location?.hash ?? ''); } catch { /* unavailable */ }
    try {
      await auth.signIn();
    } catch (e) {
      login.setBusy(false);
      login.println(e instanceof Error ? e.message : String(e));
    }
  }

  async function wakeThenConnect(): Promise<void> {
    const run = ++wakeRun;
    setView('waking');
    waking.checking();
    const wake = deps.ensureAwake ?? ((gp, op) => ensureAwake(fetchImpl, gp, op));
    let st: WakeState;
    try {
      st = await wake(getProof, (s) => { if (run === wakeRun) waking.show(s); });
    } catch (e) {
      st = { kind: 'error', message: e instanceof Error ? e.message : String(e) };
      if (run === wakeRun) waking.show(st);
    }
    if (run !== wakeRun || !mode) return;
    if (st.kind === 'running') {
      if (welcomed) return; // the socket's own retry got in while we checked
      connect();
    }
  }

  function connect(): void {
    refusal = null;
    welcomed = false;
    if (gateway) {
      gateway.reconnect();
      return;
    }
    // one control page per session; a new gateway connection (after the lab stopped) keeps it
    const page =
      control ??
      new ControlPage({
        send: (m) => gateway?.send(m),
        logout: () => void logout(),
        openHelp: () => (deps.openWindow ?? ((u, n) => void globalThis.open(u, n)))('/help.html', 'help'),
        openHistory: () => { globalThis.location.hash = '#/history'; },
        openClassic: () => { globalThis.location.hash = '#/classic'; },
      });
    control = page;
    classic ??= new ClassicPage({
      send: (m) => gateway?.send(m),
      showApplet: () => { globalThis.location.hash = '#/'; },
      openHistory: () => { globalThis.location.hash = '#/history'; },
      logout: () => void logout(),
    });
    const forms = classic;
    const make = deps.makeGateway ?? ((u, hl, o) => new Gateway(u, hl, o));
    const g = make(wsUrl(), hello, {
      onClose: () => {
        if (gateway !== g) return;
        if (refusal !== null) {
          // removed by an administrator, or banned: back to the login page, not straight back in
          const why = refusal;
          refusal = null;
          endSession(why);
          return;
        }
        welcomed = false;
        page.disconnected();
        forms.disconnected();
        if (view === 'control') {
          // the lab may have restarted: show the waking page and check it, rather than
          // leaving the last image frozen while the socket retries
          void wakeThenConnect();
        }
      },
      onWakeNeeded: () => {
        if (gateway === g) void wakeThenConnect();
      },
    });
    gateway = g;
    for (const t of OUT_TYPES) g.on(t, (m: Out) => onMessage(g, page, m));
    g.onImage((hd, jpeg) => {
      if (gateway !== g) return;
      void page.image(hd, jpeg);
      forms.image(hd, jpeg);
    });
  }

  function onMessage(g: GatewayLike, page: ControlPage, m: Out): void {
    if (gateway !== g) return;
    page.handle(m);
    classic?.handle(m);
    // the gateway closes a kicked or banned session right after telling it why
    // before the welcome, unauthorised / too-many / bad-message (a reserved nick) are terminal: the close
    // that follows must end the session, not retry. Anything else (lab-unavailable while the lab starts)
    // is transient and goes through the wake/retry flow.
    const terminal = welcomed ? ['unauthorised'] : ['unauthorised', 'too-many', 'bad-message'];
    refusal = m.t === 'error' && terminal.includes(m.code) ? m.message : null;
    if (m.t === 'welcome') {
      welcomed = true;
      admin = m.level === 'admin';
      login.setBusy(false);
      setView('control');
    } else if (m.t === 'error' && !welcomed && (m.code === 'unauthorised' || m.code === 'too-many')) {
      endSession(m.message);
    } else if (m.t === 'lab' && m.state === 'stopping') {
      // the gateway is going away; waking it again is the user's choice, not automatic
      wakeRun++;
      g.close();
      gateway = null;
      admin = false;
      welcomed = false;
      page.disconnected();
      classic?.disconnected();
      setView('waking');
      waking.stopped(m.message || 'The robot has stopped');
    }
  }

  function closeGateway(): void {
    wakeRun++;
    gateway?.close();
    gateway = null;
    admin = false;
    welcomed = false;
    control?.dispose();
    control = null;
    classic?.dispose();
    classic = null;
  }

  /** Back to the login page with a message (refused, expired). */
  function endSession(message: string): void {
    closeGateway();
    mode = null;
    login.setBusy(false);
    setView('login');
    if (message) {
      login.println(message);
      // a message needs the login page, not the landing page
      if (route() === 'landing' && globalThis.location) {
        globalThis.location.hash = APPLET_HASH;
        render();
      }
    }
  }

  /** TA200001.finishSession. */
  async function logout(): Promise<void> {
    const wasSignedIn = mode?.kind === 'signedIn';
    endSession('Logged out bye....');
    if (wasSignedIn) {
      try { await auth.signOut(); } catch { /* already signed out locally */ }
    }
  }

  // ---- the e2e hook (read-only) ----
  try {
    if (new URLSearchParams(globalThis.location?.search ?? '').get('e2e') === '1') {
      const api = Object.freeze({
        cameraMatrix: (name: string) => control?.cameras.cameraMatrix(name) ?? null,
        cameraScale: (name: string) => control?.cameras.view(name)?.canvas.scale ?? null,
        canvasRect: (name: string) => {
          const r = control?.cameras.view(name)?.canvasEl.getBoundingClientRect();
          return r ? { x: r.x, y: r.y, width: r.width, height: r.height, left: r.left, top: r.top, right: r.right, bottom: r.bottom } : null;
        },
        cursorPose: () => control?.cursorPose() ?? null,
        robotState: () => control?.robotState() ?? null,
        controller: () => control?.controller() ?? null,
        nick: () => control?.nick() ?? null,
        workspacePoints: () => control?.workspace.names().map((n) => ({ ...control!.workspace.get(n)! })) ?? null,
        tapeLength: () => control?.tapeMeasure.length() ?? null,
      });
      Object.defineProperty(globalThis, '__telerobot', { value: api, writable: false, configurable: false, enumerable: false });
    }
  } catch { /* defined already */ }

  render();

  const ready = (async () => {
    let configError: string | null = null;
    try {
      config = await (deps.config ?? (() => loadConfig()))();
    } catch (e) {
      configError = `Could not load the configuration: ${e instanceof Error ? e.message : String(e)}`;
    }
    const typed = login.nicknameField.value;
    login = makeLogin(Boolean(config.userPoolDomain && config.clientId), config.dev === true);
    login.nicknameField.value = typed;
    if (configError) login.println(configError);
    render();
    let signedIn = false;
    try {
      signedIn = await auth.handleRedirect();
    } catch (e) {
      login.println(e instanceof Error ? e.message : String(e));
    }
    if (signedIn) {
      let back: string | null = null;
      try {
        back = globalThis.sessionStorage?.getItem(AFTER_SIGN_IN) ?? null;
        globalThis.sessionStorage?.removeItem(AFTER_SIGN_IN);
      } catch { /* unavailable */ }
      if (back === APPLET_HASH || back === FORMS_HASH) globalThis.location.hash = back;
      if (nick) begin(nick, { kind: 'signedIn' });
      else login.println('Signed in: type a nickname and press Sign in');
    }
  })();

  return {
    root,
    view: route,
    control: () => control,
    classic: () => classic,
    get login() { return login; },
    waking,
    ready,
  };
}
