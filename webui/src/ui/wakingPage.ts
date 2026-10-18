// Shown while the lab instance is woken (or the gateway reconnected): what to
// expect, the steps so far, a progress bar paced on measured wakes, an arm going
// through a pick-and-place, and facts about the telerobot to read meanwhile; or
// the hours-cap message, or an error with Retry.

import type { WakeState, WakeStep } from '../net/wake';
import { button, h } from './dom';

export const WAKING = 'Turning the robot on…';
export const CAPPED = 'The robot is resting until next month';
export const EXPECT = 'The robot sleeps when no one is using it. Waking it usually takes under a minute.';
export const SLOW = 'Taking longer than usual…';

/**
 * A typical wake, from the click to the control page; the bar is paced on it.
 * Measured 2026-10-18 on the live site: 39 s (the instance answered 32-33 s
 * after it was started, through both the London and Manchester edges).
 */
export const TYPICAL_WAKE_S = 40;
/** Past this the page says the wake is slow. */
export const SLOW_AFTER_S = 90;
export const FACT_MS = 9000;

export interface Fact {
  text: string;
  link?: { href: string; label: string };
}

export const WIKIPEDIA = 'https://en.wikipedia.org/wiki/UWA_Telerobot';
/** The original telerobot.mech.uwa.edu.au copy is gone; the Wayback Machine kept it. */
export const DALTON_THESIS =
  'https://web.archive.org/web/20081203110131/http://telerobot.mech.uwa.edu.au/Information/Dalton%20thesis.pdf';

/** Paraphrased from the Wikipedia article (WIKIPEDIA), plus how this site works. */
export const FACTS: readonly Fact[] = [
  { text: 'The UWA Telerobot went online in 1994, one of the first robots anyone on the web could control.' },
  { text: 'It began as Ken Taylor’s PhD project at the University of Western Australia.' },
  { text: 'A few weeks earlier, a toy robot at USC (Ken Goldberg’s Mercury Project) had gone online. It ran for about seven months.' },
  { text: 'In 1996 the first arm was replaced by an ABB IRB1400: a six-axis industrial robot with a pneumatic gripper.' },
  { text: 'At first you drove it through web forms (HTML and CGI), one move at a time.' },
  {
    text: 'Barney Dalton’s PhD added a Java applet with an augmented-reality view. This site recreates that interface.',
    link: { href: DALTON_THESIS, label: 'Techniques for Web Telerobotics (PDF)' },
  },
  { text: 'Behind this page, the original 2000 server software drives a simulated robot and cameras.' },
  { text: 'While you wait, History shows the moves other people have made.' },
];

const STEPS: readonly { id: WakeStep | 'connect'; label: string }[] = [
  { id: 'computer', label: 'Switching on the lab computer' },
  { id: 'robot', label: 'Starting the robot and cameras' },
  { id: 'connect', label: 'Connecting' },
];

/**
 * Fraction of the bar for t seconds: steady to 85% at a typical wake, then ever
 * slower towards 98%, so it never claims to be done before it is.
 */
export function progressAt(t: number): number {
  const s = Math.max(0, t);
  if (s <= TYPICAL_WAKE_S) return (0.85 * s) / TYPICAL_WAKE_S;
  return 0.85 + 0.13 * (1 - Math.exp(-(s - TYPICAL_WAKE_S) / 30));
}

const SVG = 'http://www.w3.org/2000/svg';
function svg(tag: string, attrs: Record<string, string | number>, ...children: Element[]): SVGElement {
  const el = document.createElementNS(SVG, tag);
  for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, String(v));
  for (const c of children) el.appendChild(c);
  return el;
}

/** A two-link arm with a gripper, carrying a block back and forth (CSS keyframes in styles.css). */
function arm(): SVGElement {
  const gripper = svg('g', { class: 'arm-gripper' },
    svg('rect', { x: -2, y: 0, width: 4, height: 6 }),
    svg('rect', { x: -7, y: 6, width: 14, height: 3 }),
    svg('rect', { x: -7, y: 9, width: 3, height: 7 }),
    svg('rect', { x: 4, y: 9, width: 3, height: 7 }),
    svg('rect', { x: -4, y: 9, width: 8, height: 8, class: 'arm-block' }),
  );
  const forearm = svg('g', { class: 'arm-forearm' },
    svg('rect', { x: -4, y: -4, width: 46, height: 8, rx: 4 }),
    svg('circle', { cx: 0, cy: 0, r: 5, class: 'arm-joint' }),
    svg('g', { transform: 'translate(42 0)' }, gripper),
  );
  const upper = svg('g', { class: 'arm-upper' },
    svg('rect', { x: -5, y: -5, width: 52, height: 10, rx: 5 }),
    svg('circle', { cx: 0, cy: 0, r: 6, class: 'arm-joint' }),
    svg('g', { transform: 'translate(47 0)' }, forearm),
  );
  // the viewBox leaves room above for the arm reaching straight up
  return svg('svg', { class: 'waking-arm', viewBox: '0 -24 160 124', 'aria-hidden': 'true' },
    svg('rect', { x: 0, y: 92, width: 160, height: 8, class: 'arm-table' }),
    svg('rect', { x: 66, y: 74, width: 28, height: 18, class: 'arm-base' }),
    svg('g', { transform: 'translate(80 72)' }, upper),
  );
}

export class WakingPage {
  readonly el: HTMLElement;
  readonly heading: HTMLElement;
  readonly detail: HTMLElement;
  readonly expect: HTMLElement;
  readonly steps: HTMLOListElement;
  readonly bar: HTMLElement;
  readonly fact: HTMLElement;
  readonly retry: HTMLButtonElement;
  readonly back: HTMLButtonElement;
  private readonly progress: HTMLElement;
  private readonly waitBox: HTMLElement;
  private timer: ReturnType<typeof setInterval> | null = null;
  private factTimer: ReturnType<typeof setInterval> | null = null;
  private factIndex = 0;

  constructor(
    onRetry: () => void,
    onBack: () => void,
    private readonly now: () => number = Date.now,
  ) {
    this.heading = h('div', { class: 'waking-heading', role: 'status', text: WAKING });
    this.detail = h('div', { class: 'waking-detail' });
    this.expect = h('div', { class: 'waking-expect', text: EXPECT });
    this.steps = h('ol', { class: 'waking-steps' },
      ...STEPS.map((s) => h('li', { 'data-step': s.id, class: 'todo', text: s.label })));
    this.bar = h('div', { class: 'waking-bar-fill' });
    this.progress = h('div', {
      class: 'waking-bar', role: 'progressbar', 'aria-label': 'Waking the robot',
      'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': 0,
    }, this.bar);
    this.fact = h('p', { class: 'waking-fact', 'aria-live': 'polite' });
    this.waitBox = h('div', { class: 'waking-wait' },
      arm(), this.expect, this.steps, this.progress,
      h('div', { class: 'waking-did-you-know' },
        h('div', { class: 'waking-fact-title', text: 'Did you know?' }),
        this.fact,
        h('a', { class: 'waking-source', href: WIKIPEDIA, target: '_blank', rel: 'noopener', text: 'More on Wikipedia' })),
    );
    this.waitBox.hidden = true;
    this.retry = button('Retry', onRetry);
    this.retry.hidden = true;
    this.back = button('Back to login', onBack);
    this.back.hidden = true;
    this.el = h(
      'div',
      { class: 'page waking-page' },
      h(
        'fieldset',
        { class: 'border-panel waking-box' },
        h('legend', { text: 'Telerobot' }),
        this.heading,
        this.waitBox,
        this.detail,
        h('div', { class: 'button-row' }, this.retry, this.back),
        h('a', { href: '#/history', class: 'history-link', text: 'History' }),
      ),
    );
  }

  /** Reflects a WakeState from ensureAwake. */
  show(s: WakeState): void {
    this.retry.hidden = true;
    this.back.hidden = s.kind !== 'capped' && s.kind !== 'error';
    if (s.kind === 'starting') {
      this.heading.textContent = WAKING;
      this.markSteps(s.step);
      this.waitBox.hidden = false;
      if (this.timer) clearInterval(this.timer);
      const tick = () => {
        const t = Math.max(0, Math.floor((this.now() - s.since) / 1000));
        this.setProgress(progressAt(t));
        this.detail.textContent = t >= SLOW_AFTER_S ? `${t} s · ${SLOW}` : `${t} s`;
      };
      tick();
      this.timer = setInterval(tick, 1000);
      this.startFacts();
      return;
    }
    this.stopTimer();
    switch (s.kind) {
      case 'running':
        // the last step: keep the wait on screen, finished, while the gateway connects
        this.heading.textContent = 'Connecting…';
        if (!this.waitBox.hidden) {
          this.markSteps('connect');
          this.setProgress(1);
        }
        this.detail.textContent = '';
        break;
      case 'capped':
        this.heading.textContent = CAPPED;
        this.waitBox.hidden = true;
        this.detail.textContent = '';
        break;
      case 'error':
        this.heading.textContent = 'The robot could not be turned on';
        this.waitBox.hidden = true;
        this.detail.textContent = s.message;
        this.retry.textContent = 'Retry';
        this.retry.hidden = false;
        break;
    }
  }

  /** A message with a button to carry on (e.g. the lab stopped, or the session was closed). */
  stopped(message: string, label = 'Reconnect'): void {
    this.stopTimer();
    this.waitBox.hidden = true;
    this.heading.textContent = message || 'The robot has stopped';
    this.detail.textContent = '';
    this.retry.textContent = label;
    this.retry.hidden = false;
    this.back.hidden = false;
  }

  /** Before ensureAwake has reported. */
  checking(): void {
    this.stopTimer();
    this.waitBox.hidden = true;
    this.retry.textContent = 'Retry';
    this.retry.hidden = true;
    this.back.hidden = true;
    this.heading.textContent = 'Connecting…';
    this.detail.textContent = '';
  }

  stopTimer(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    if (this.factTimer) clearInterval(this.factTimer);
    this.factTimer = null;
  }

  /** Earlier steps done, this one active, later ones to do. */
  private markSteps(current: WakeStep | 'connect'): void {
    const at = STEPS.findIndex((s) => s.id === current);
    [...this.steps.children].forEach((li, i) => {
      li.className = i < at ? 'done' : i === at ? 'active' : 'todo';
      if (i === at) li.setAttribute('aria-current', 'step');
      else li.removeAttribute('aria-current');
    });
  }

  private setProgress(f: number): void {
    const pct = Math.round(f * 100);
    this.bar.style.width = `${pct}%`;
    this.progress.setAttribute('aria-valuenow', String(pct));
  }

  /** Shows a fact and moves on every FACT_MS; carries on from where the last wait left off. */
  private startFacts(): void {
    if (this.factTimer) return;
    this.showFact();
    this.factTimer = setInterval(() => {
      this.factIndex = (this.factIndex + 1) % FACTS.length;
      this.showFact();
    }, FACT_MS);
  }

  private showFact(): void {
    const f = FACTS[this.factIndex % FACTS.length]!;
    this.fact.replaceChildren(f.text);
    if (f.link) {
      this.fact.append(' ', h('a', { href: f.link.href, target: '_blank', rel: 'noopener', text: f.link.label }));
    }
  }
}
