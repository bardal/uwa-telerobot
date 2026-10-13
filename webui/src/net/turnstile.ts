export const TURNSTILE_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

/** The subset of window.turnstile that we use. */
export interface TurnstileApi {
  render(container: HTMLElement, options: {
    sitekey: string;
    appearance?: 'always' | 'execute' | 'interaction-only';
    callback: (token: string) => void;
    'error-callback': (code?: string) => void;
    'expired-callback'?: () => void;
  }): string;
  reset(widgetId: string): void;
  remove?(widgetId: string): void;
}

let apiPromise: Promise<TurnstileApi> | null = null;

/** Loads Turnstile's api.js by adding a script element (no inline script). */
export function loadTurnstile(doc: Document = document): Promise<TurnstileApi> {
  const existing = (globalThis as { turnstile?: TurnstileApi }).turnstile;
  if (existing) return Promise.resolve(existing);
  apiPromise ??= new Promise<TurnstileApi>((resolve, reject) => {
    const s = doc.createElement('script');
    s.src = TURNSTILE_SRC;
    s.async = true;
    s.onload = () => {
      const api = (globalThis as { turnstile?: TurnstileApi }).turnstile;
      if (api) resolve(api); else reject(new Error('Turnstile did not initialise'));
    };
    s.onerror = () => { apiPromise = null; reject(new Error('Could not load Turnstile')); };
    doc.head.appendChild(s);
  });
  return apiPromise;
}

export interface TurnstileOptions {
  siteKey: string;
  container: HTMLElement;
  /** Injectable for tests; defaults to loading Cloudflare's script. */
  api?: TurnstileApi | Promise<TurnstileApi>;
}

/** A managed, interaction-only widget; token() yields a fresh token each call. */
export class TurnstileWidget {
  private widgetId: string | null = null;
  private pending: { resolve: (t: string) => void; reject: (e: Error) => void } | null = null;
  private apiP: Promise<TurnstileApi>;

  constructor(private readonly opts: TurnstileOptions) {
    this.apiP = Promise.resolve(opts.api ?? loadTurnstile());
  }

  async token(): Promise<string> {
    const api = await this.apiP;
    return new Promise<string>((resolve, reject) => {
      this.pending?.reject(new Error('Superseded by a newer Turnstile request'));
      this.pending = { resolve, reject };
      if (this.widgetId === null) {
        this.widgetId = api.render(this.opts.container, {
          sitekey: this.opts.siteKey,
          appearance: 'interaction-only',
          callback: (t) => { const p = this.pending; this.pending = null; p?.resolve(t); },
          'error-callback': (code) => {
            const p = this.pending; this.pending = null;
            p?.reject(new Error(`Turnstile failed${code ? ` (${code})` : ''}`));
          },
        });
      } else {
        api.reset(this.widgetId);
      }
    });
  }

  destroy(): void {
    if (this.widgetId !== null) void this.apiP.then((a) => a.remove?.(this.widgetId as string));
    this.widgetId = null;
  }
}
