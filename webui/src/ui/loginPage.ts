// The login page: a guest / registered-user switch, a nickname, and which
// interface the session will open (the 2000 applet page or the 1996 forms page),
// over a console for messages. Guests pass Turnstile; Sign in uses Cognito's
// hosted page for the email and password. (The applet's LoginPanel row of
// username, PIN and nickname boxes was replaced: Cognito asks for the account.)

import { ConsolePanel } from './consolePanel';
import { button, h } from './dom';

export const ARCHIVE_NOTICE =
  'Chat, moves and camera images are public and are kept in the archive. The administrator can remove them.';

export const GUEST_HINT = 'Anyone can drive as a guest. Choose a nickname; other people will see it.';
export const REGISTERED_HINT =
  'Sign in on the next page with your email and password, or create an account there. ' +
  'Your nickname is what other people will see.';

export type LoginMode = 'guest' | 'registered';

/** The gateway's nick rule (Limits.cleanNick): 1 to 20 of letters, digits, space, _ . - after trimming. */
export function cleanNick(raw: string): string | null {
  const n = raw.trim();
  return /^[A-Za-z0-9 _.-]{1,20}$/.test(n) ? n : null;
}

export interface LoginPageOpts {
  nick: string;
  /** Cognito is configured (a user pool domain). */
  signInAvailable: boolean;
  /** Dev builds: a dev sign-in with an optional admin group. */
  dev: boolean;
  /** Guest or registered user, as last chosen (registered needs signInAvailable). */
  mode?: LoginMode;
  onMode?: (mode: LoginMode) => void;
  onGuest: (nick: string) => void;
  onSignIn: (nick: string) => void;
  onDev: (nick: string, admin: boolean) => void;
}

export class LoginPage {
  readonly el: HTMLElement;
  readonly nicknameField: HTMLInputElement;
  readonly turnstileContainer: HTMLElement;
  readonly console = new ConsolePanel(4, 'login-console');
  readonly guestButton: HTMLButtonElement;
  readonly signInButton: HTMLButtonElement;
  private readonly buttons: HTMLButtonElement[] = [];
  private readonly radios: Record<LoginMode, HTMLInputElement>;
  private readonly hint: HTMLElement;
  private readonly way: HTMLElement;
  private mode: LoginMode;

  constructor(private readonly opts: LoginPageOpts) {
    this.mode = opts.mode === 'registered' && opts.signInAvailable ? 'registered' : 'guest';

    const radio = (m: LoginMode, label: string) => {
      const r = h('input', { type: 'radio', name: 'login-mode', value: m, class: 'login-mode-input' });
      r.addEventListener('change', () => { if (r.checked) this.setMode(m, true); });
      return { r, el: h('label', { class: 'login-mode' }, r, h('span', { text: label })) };
    };
    const guestRadio = radio('guest', 'Guest');
    const registeredRadio = radio('registered', 'Registered user');
    registeredRadio.r.disabled = !opts.signInAvailable;
    if (!opts.signInAvailable) registeredRadio.el.title = 'Sign-in is not configured here';
    this.radios = { guest: guestRadio.r, registered: registeredRadio.r };

    this.nicknameField = h('input', {
      type: 'text', class: 'login-input', maxlength: 20, 'aria-label': 'nickname', autocomplete: 'nickname', spellcheck: 'false',
    });
    this.nicknameField.value = opts.nick;

    this.guestButton = button('Continue as guest', () => this.go((n) => this.opts.onGuest(n)), 'login-go');
    this.signInButton = button('Sign in', () => this.go((n) => this.opts.onSignIn(n)), 'login-go');
    this.signInButton.disabled = !opts.signInAvailable;
    this.buttons.push(this.guestButton, this.signInButton);
    this.nicknameField.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') (this.mode === 'guest' ? this.guestButton : this.signInButton).click();
    });

    this.hint = h('p', { class: 'login-hint' });
    this.way = h('p', { class: 'login-way' });

    const card = h(
      'div',
      { class: 'login-card' },
      h('h1', { class: 'login-title', text: 'Drive the robot' }),
      this.way,
      h('div', { class: 'login-modes', role: 'radiogroup', 'aria-label': 'How to log in' }, guestRadio.el, registeredRadio.el),
      h('label', { class: 'login-field' }, h('span', { text: 'Nickname' }), this.nicknameField),
      this.hint,
      h('div', { class: 'login-actions' }, this.guestButton, this.signInButton),
    );
    if (opts.dev) {
      const admin = h('input', { type: 'checkbox', class: 'awt-check', 'aria-label': 'admin' });
      const dev = button('Dev sign in', () => this.go((n) => this.opts.onDev(n, admin.checked)));
      this.buttons.push(dev);
      card.append(h('div', { class: 'login-actions' }, dev, h('label', { class: 'awt-label dev-admin' }, admin, ' admin')));
    }

    this.turnstileContainer = h('div', { class: 'turnstile-container' });
    const notice = h(
      'p',
      { class: 'archive-notice' },
      h('span', { text: `${ARCHIVE_NOTICE} ` }),
      h('a', { href: '#/history', class: 'history-link', text: 'History' }),
      ' · ',
      h('a', { href: '#/', class: 'home-link', text: 'Home' }),
    );
    card.append(this.turnstileContainer, this.console.el, notice);
    this.el = h('div', { class: 'page site-page login-page' }, card);
    this.setMode(this.mode, false);
    this.setWay(false);
  }

  /** Which interface the session opens: the 1996 forms page, or the 2000 applet page. */
  setWay(forms: boolean): void {
    const [now, other, otherHash] = forms
      ? ['the forms interface (1996)', 'the Java applet interface (2000)', '#/login']
      : ['the Java applet interface (2000)', 'the forms interface (1996)', '#/classic'];
    this.way.replaceChildren(
      'You’ll use ', h('strong', { text: now }), '. ',
      h('a', { href: otherHash, class: 'login-switch', text: `Use ${other} instead` }),
    );
  }

  currentMode(): LoginMode {
    return this.mode;
  }

  private setMode(m: LoginMode, chosen: boolean): void {
    this.mode = m;
    this.radios[m].checked = true;
    this.guestButton.hidden = m !== 'guest';
    this.signInButton.hidden = m !== 'registered';
    this.hint.textContent = m === 'guest' ? GUEST_HINT : REGISTERED_HINT;
    if (chosen) this.opts.onMode?.(m);
  }

  println(s: string): void {
    this.console.println(s);
  }

  /** LoginPanel disables its button while a login is under way. */
  setBusy(b: boolean): void {
    for (const x of this.buttons) {
      if (x === this.signInButton && !this.opts.signInAvailable) continue;
      x.disabled = b;
    }
  }

  private go(start: (nick: string) => void): void {
    const raw = this.nicknameField.value;
    if (!raw.trim()) {
      this.println('Please type a nickname');
      this.nicknameField.focus();
      return;
    }
    const nick = cleanNick(raw);
    if (!nick) {
      this.println('A nickname is 1 to 20 letters, digits, spaces, _ . or -');
      this.nicknameField.focus();
      return;
    }
    this.println('Logging in... please wait.....');
    start(nick);
  }
}
