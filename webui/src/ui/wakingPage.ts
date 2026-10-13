// Shown while the lab instance is woken (or the gateway reconnected): progress
// with elapsed seconds, the hours-cap message, or an error with Retry.

import type { WakeState } from '../net/wake';
import { button, h } from './dom';

export const WAKING = 'Turning the robot on…';
export const CAPPED = 'The robot is resting until next month';

export class WakingPage {
  readonly el: HTMLElement;
  readonly heading: HTMLElement;
  readonly detail: HTMLElement;
  readonly retry: HTMLButtonElement;
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(
    onRetry: () => void,
    private readonly now: () => number = Date.now,
  ) {
    this.heading = h('div', { class: 'waking-heading', role: 'status', text: WAKING });
    this.detail = h('div', { class: 'waking-detail' });
    this.retry = button('Retry', onRetry);
    this.retry.hidden = true;
    this.el = h(
      'div',
      { class: 'page waking-page' },
      h(
        'fieldset',
        { class: 'border-panel waking-box' },
        h('legend', { text: 'Telerobot' }),
        this.heading,
        this.detail,
        h('div', { class: 'button-row' }, this.retry),
        h('a', { href: '#/history', class: 'history-link', text: 'History' }),
      ),
    );
  }

  /** Reflects a WakeState from ensureAwake. */
  show(s: WakeState): void {
    this.stopTimer();
    this.retry.hidden = true;
    switch (s.kind) {
      case 'starting': {
        this.heading.textContent = WAKING;
        const tick = () => {
          this.detail.textContent = `${Math.max(0, Math.floor((this.now() - s.since) / 1000))} s`;
        };
        tick();
        this.timer = setInterval(tick, 1000);
        break;
      }
      case 'running':
        this.heading.textContent = 'Connecting…';
        this.detail.textContent = '';
        break;
      case 'capped':
        this.heading.textContent = CAPPED;
        this.detail.textContent = '';
        break;
      case 'error':
        this.heading.textContent = 'The robot could not be turned on';
        this.detail.textContent = s.message;
        this.retry.textContent = 'Retry';
        this.retry.hidden = false;
        break;
    }
  }

  /** A message with a button to carry on (e.g. the lab stopped, or the session was closed). */
  stopped(message: string, label = 'Reconnect'): void {
    this.stopTimer();
    this.heading.textContent = message || 'The robot has stopped';
    this.detail.textContent = '';
    this.retry.textContent = label;
    this.retry.hidden = false;
  }

  /** Before ensureAwake has reported. */
  checking(): void {
    this.stopTimer();
    this.retry.textContent = 'Retry';
    this.retry.hidden = true;
    this.heading.textContent = 'Connecting…';
    this.detail.textContent = '';
  }

  stopTimer(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }
}
