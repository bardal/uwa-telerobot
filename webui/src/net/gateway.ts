import type { Config } from '../config';
import type { In, Out } from './protocol';

type Handler<T extends Out['t']> = (m: Extract<Out, { t: T }>) => void;
type ImageHeader = Extract<Out, { t: 'image' }>;

export interface GatewayOpts {
  WebSocketImpl?: typeof WebSocket;
  /** Called after 3 consecutive failed connections; the caller should run ensureAwake then reconnect(). */
  onWakeNeeded?: () => void;
}

export const BACKOFF_MS = [1000, 2000, 4000, 8000];
export const BACKOFF_CAP_MS = 15000;
export const WAKE_AFTER_FAILURES = 3;

/** Delay before the reconnect that follows failure number `failures` (1-based): 1, 2, 4, 8, then 15 s. */
export function backoffDelay(failures: number): number {
  const i = Math.max(0, failures - 1);
  return Math.min(BACKOFF_MS[i] ?? BACKOFF_CAP_MS, BACKOFF_CAP_MS);
}

export class Gateway {
  state: 'connecting' | 'open' | 'closed' = 'connecting';
  private ws: WebSocket | null = null;
  private handlers = new Map<string, ((m: never) => void)[]>();
  private imageHandlers: ((h: ImageHeader, jpeg: Blob) => void)[] = [];
  private pendingImage: ImageHeader | null = null;
  private failures = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private stopped = false;
  private readonly WS: typeof WebSocket;

  constructor(
    private readonly url: string,
    private readonly hello: () => Promise<In & { t: 'hello' }>,
    private readonly opts: GatewayOpts = {},
  ) {
    this.WS = opts.WebSocketImpl ?? WebSocket;
    this.connect();
  }

  on<T extends Out['t']>(t: T, fn: Handler<T>): void {
    const list = this.handlers.get(t) ?? [];
    list.push(fn as (m: never) => void);
    this.handlers.set(t, list);
  }

  onImage(fn: (header: ImageHeader, jpeg: Blob) => void): void {
    this.imageHandlers.push(fn);
  }

  send(m: In): void {
    if (this.ws && this.state === 'open') this.ws.send(JSON.stringify(m));
  }

  close(): void {
    this.stopped = true;
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.state = 'closed';
    this.ws?.close();
  }

  /** Restart connecting (e.g. after the wake flow succeeded). */
  reconnect(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.stopped = false;
    this.failures = 0;
    this.connect();
  }

  private connect(): void {
    this.state = 'connecting';
    this.pendingImage = null;
    const ws = new this.WS(this.url);
    ws.binaryType = 'blob';
    this.ws = ws;
    ws.onopen = () => {
      if (this.ws !== ws) return;
      this.state = 'open';
      this.failures = 0;
      this.hello().then(
        (h) => { if (this.ws === ws && this.state === 'open') ws.send(JSON.stringify(h)); },
        () => { ws.close(); },
      );
    };
    ws.onmessage = (ev: MessageEvent) => {
      if (this.ws === ws) this.onFrame(ev.data as unknown);
    };
    ws.onclose = () => {
      if (this.ws !== ws) return;
      this.pendingImage = null;
      this.state = 'closed';
      if (this.stopped) return;
      this.failures++;
      if (this.failures === WAKE_AFTER_FAILURES) {
        // The wake flow takes over; it calls reconnect() when the lab is up.
        this.opts.onWakeNeeded?.();
        return;
      }
      this.timer = setTimeout(() => { this.timer = null; this.connect(); }, backoffDelay(this.failures));
    };
    ws.onerror = () => { /* onclose follows */ };
  }

  private onFrame(data: unknown): void {
    if (typeof data === 'string') {
      let msg: unknown;
      try { msg = JSON.parse(data); } catch { return; }
      if (typeof msg !== 'object' || msg === null || typeof (msg as { t?: unknown }).t !== 'string') return;
      const m = msg as Out;
      if (m.t === 'image') {
        this.pendingImage = m;
        return;
      }
      this.pendingImage = null;
      for (const fn of this.handlers.get(m.t) ?? []) (fn as (x: Out) => void)(m);
      return;
    }
    const header = this.pendingImage;
    this.pendingImage = null;
    if (!header) return; // binary frame without a header: dropped
    const blob = data instanceof Blob ? data : new Blob([data as ArrayBuffer], { type: 'image/jpeg' });
    for (const fn of this.imageHandlers) fn(header, blob);
  }
}

export interface HelloInput {
  nick: string;
  proof: { turnstile?: string; idToken?: string };
  config: Pick<Config, 'dev'>;
  devIdentity?: { sub: string; groups: string[] };
}

/** Builds the hello: dev identity (dev builds only), else Cognito token, else Turnstile token. */
export function buildHello(i: HelloInput): In & { t: 'hello' } {
  const h: In & { t: 'hello' } = { t: 'hello', nick: i.nick };
  if (i.config.dev && i.devIdentity) h.dev = i.devIdentity;
  else if (i.proof.idToken) h.idToken = i.proof.idToken;
  else if (i.proof.turnstile) h.turnstile = i.proof.turnstile;
  return h;
}
