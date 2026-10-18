// The login page: mom/gui/LoginPanel's row over the applet's help text and its
// console (TA200001's loginPage). Guests pass Turnstile; Sign in uses Cognito.

import { ConsolePanel } from './consolePanel';
import { button, h } from './dom';

/** TA200001's help text, verbatim. */
export const HELP_TEXT =
  "Login using your username, PIN and type a nickname.\nGuests don't need to worry about a PIN, but you should use a nickname.\n After you login you should see images of the robot.\nTry and gain control and move it.\nNo help is available for the interface yet!!!!";

export const ARCHIVE_NOTICE =
  'Chat, moves and camera images are public and are kept in the archive. The administrator can remove them.';

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
  onGuest: (nick: string) => void;
  onSignIn: (nick: string) => void;
  onDev: (nick: string, admin: boolean) => void;
}

export class LoginPage {
  readonly el: HTMLElement;
  readonly nicknameField: HTMLInputElement;
  readonly turnstileContainer: HTMLElement;
  readonly console = new ConsolePanel(4, 'login-console');
  private readonly buttons: HTMLButtonElement[] = [];

  constructor(private readonly opts: LoginPageOpts) {
    const username = h('input', { type: 'text', class: 'awt-text', size: 7, value: 'guest', 'aria-label': 'username', disabled: true, title: 'Sign in asks for your account on the next page' });
    const password = h('input', { type: 'password', class: 'awt-text', size: 3, 'aria-label': 'password', disabled: true, title: 'Sign in asks for your password on the next page' });
    this.nicknameField = h('input', { type: 'text', class: 'awt-text', size: 10, maxlength: 20, 'aria-label': 'nickname', autocomplete: 'nickname' });
    this.nicknameField.value = opts.nick;

    const guest = button('Guest', () => this.go((n) => this.opts.onGuest(n)));
    const signIn = button('Sign in', () => this.go((n) => this.opts.onSignIn(n)));
    signIn.disabled = !opts.signInAvailable;
    if (!opts.signInAvailable) signIn.title = 'Sign-in is not configured here';
    this.buttons.push(guest, signIn);
    this.nicknameField.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') guest.click();
    });

    const row = h(
      'div',
      { class: 'login-panel' },
      h('label', { class: 'awt-label', text: 'username' }),
      username,
      h('label', { class: 'awt-label', text: 'password' }),
      password,
      h('label', { class: 'awt-label', text: 'nickname' }),
      this.nicknameField,
      guest,
      signIn,
    );
    if (opts.dev) {
      const admin = h('input', { type: 'checkbox', class: 'awt-check', 'aria-label': 'admin' });
      const dev = button('Dev sign in', () => this.go((n) => this.opts.onDev(n, admin.checked)));
      this.buttons.push(dev);
      row.append(dev, h('label', { class: 'awt-label dev-admin' }, admin, ' admin'));
    }

    this.turnstileContainer = h('div', { class: 'turnstile-container' });
    const help = h('textarea', { class: 'login-help', readonly: true, rows: 6, 'aria-label': 'Help' });
    help.value = HELP_TEXT;
    const notice = h(
      'div',
      { class: 'archive-notice' },
      h('span', { text: `${ARCHIVE_NOTICE} ` }),
      h('a', { href: '#/history', class: 'history-link', text: 'History' }),
      ' · ',
      h('a', { href: '#/', class: 'about-link', text: 'About the UWA Telerobot' }),
    );
    this.el = h('div', { class: 'page login-page' }, row, this.turnstileContainer, help, notice, this.console.el);
  }

  println(s: string): void {
    this.console.println(s);
  }

  /** LoginPanel disables its button while a login is under way. */
  setBusy(b: boolean): void {
    for (const x of this.buttons) {
      if (x.textContent === 'Sign in' && !this.opts.signInAvailable) continue;
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
