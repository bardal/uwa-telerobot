import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { backoffDelay, buildHello, Gateway } from '../src/net/gateway';
import type { Out } from '../src/net/protocol';

class FakeWS {
  static instances: FakeWS[] = [];
  binaryType = 'blob';
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((e: { data: unknown }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  constructor(public url: string) { FakeWS.instances.push(this); }
  send(s: string) { this.sent.push(s); }
  close() { this.closed = true; }
  open() { this.onopen?.(); }
  msg(data: unknown) { this.onmessage?.({ data }); }
  drop() { this.onclose?.(); }
}
const WS = FakeWS as unknown as typeof WebSocket;
const hello = () => Promise.resolve({ t: 'hello' as const, nick: 'bob', turnstile: 'tok' });

const imageHeader: Out = { t: 'image', camera: 'c1', seq: 1, width: 2, height: 2, calibration: [] };

beforeEach(() => { FakeWS.instances = []; vi.useFakeTimers(); });
afterEach(() => { vi.useRealTimers(); });

describe('Gateway', () => {
  it('sends hello on open and reports state', async () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    expect(g.state).toBe('connecting');
    const ws = FakeWS.instances[0]!;
    ws.open();
    expect(g.state).toBe('open');
    await Promise.resolve(); await Promise.resolve();
    expect(JSON.parse(ws.sent[0]!)).toEqual({ t: 'hello', nick: 'bob', turnstile: 'tok' });
    g.send({ t: 'activity' });
    expect(ws.sent[1]).toBe('{"t":"activity"}');
  });

  it('asks for the hello while the socket opens, and sends it once open', async () => {
    const asked = vi.fn(hello);
    new Gateway('ws://x/ws', asked, { WebSocketImpl: WS });
    expect(asked).toHaveBeenCalledTimes(1);
    const ws = FakeWS.instances[0]!;
    await Promise.resolve();
    expect(ws.sent).toHaveLength(0);
    ws.open();
    await Promise.resolve();
    expect(JSON.parse(ws.sent[0]!).t).toBe('hello');
    expect(asked).toHaveBeenCalledTimes(1);
  });

  it('a hello that fails closes the socket once it opens', async () => {
    new Gateway('ws://x/ws', () => Promise.reject(new Error('no token')), { WebSocketImpl: WS });
    const ws = FakeWS.instances[0]!;
    await Promise.resolve();
    ws.open();
    await Promise.resolve();
    expect(ws.closed).toBe(true);
    expect(ws.sent).toHaveLength(0);
  });

  it('dispatches typed messages', () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    const got: string[] = [];
    g.on('chat', (m) => got.push(m.text));
    const ws = FakeWS.instances[0]!;
    ws.open();
    ws.msg(JSON.stringify({ t: 'chat', id: '1', nick: 'a', text: 'hi', time: 1 }));
    ws.msg(JSON.stringify({ t: 'users', nicks: [] }));
    ws.msg('not json');
    expect(got).toEqual(['hi']);
  });

  it('pairs an image header with the next binary frame', () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    const got: [string, number][] = [];
    g.onImage((h, b) => got.push([h.camera, b.size]));
    const ws = FakeWS.instances[0]!;
    ws.open();
    ws.msg(JSON.stringify(imageHeader));
    ws.msg(new Blob([new Uint8Array(5)]));
    ws.msg(new Blob([new Uint8Array(7)])); // second binary frame with no header: dropped
    expect(got).toEqual([['c1', 5]]);
  });

  it('drops a binary frame that arrives with no header', () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    const onImage = vi.fn();
    g.onImage(onImage);
    const ws = FakeWS.instances[0]!;
    ws.open();
    ws.msg(new ArrayBuffer(4));
    expect(onImage).not.toHaveBeenCalled();
  });

  it('accepts ArrayBuffer binary frames', () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    const sizes: number[] = [];
    g.onImage((_h, b) => sizes.push(b.size));
    const ws = FakeWS.instances[0]!;
    ws.open();
    ws.msg(JSON.stringify(imageHeader));
    ws.msg(new ArrayBuffer(4));
    expect(sizes).toEqual([4]);
  });

  it('a text message between header and binary cancels the pairing', () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    const onImage = vi.fn();
    g.onImage(onImage);
    const ws = FakeWS.instances[0]!;
    ws.open();
    ws.msg(JSON.stringify(imageHeader));
    ws.msg(JSON.stringify({ t: 'users', nicks: [] }));
    ws.msg(new ArrayBuffer(4));
    expect(onImage).not.toHaveBeenCalled();
  });

  it('backoff is 1, 2, 4, 8 then capped at 15 s', () => {
    expect([1, 2, 3, 4, 5, 6, 20].map(backoffDelay)).toEqual([1000, 2000, 4000, 8000, 15000, 15000, 15000]);
  });

  it('reconnects with backoff and asks for a wake after 3 failures', () => {
    const onWakeNeeded = vi.fn();
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS, onWakeNeeded });
    FakeWS.instances[0]!.drop();
    expect(FakeWS.instances).toHaveLength(1);
    vi.advanceTimersByTime(999);
    expect(FakeWS.instances).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(FakeWS.instances).toHaveLength(2);
    FakeWS.instances[1]!.drop();
    vi.advanceTimersByTime(1999);
    expect(FakeWS.instances).toHaveLength(2);
    vi.advanceTimersByTime(1);
    expect(FakeWS.instances).toHaveLength(3);
    expect(onWakeNeeded).not.toHaveBeenCalled();
    FakeWS.instances[2]!.drop();
    expect(onWakeNeeded).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(60000);
    expect(FakeWS.instances).toHaveLength(3); // waits for the wake flow
    g.reconnect();
    expect(FakeWS.instances).toHaveLength(4);
  });

  it('a successful open resets the failure count', () => {
    const onWakeNeeded = vi.fn();
    new Gateway('ws://x/ws', hello, { WebSocketImpl: WS, onWakeNeeded });
    for (let i = 0; i < 2; i++) { FakeWS.instances.at(-1)!.drop(); vi.advanceTimersByTime(20000); }
    FakeWS.instances.at(-1)!.open();
    FakeWS.instances.at(-1)!.msg(JSON.stringify({ t: 'welcome', nick: 'a', level: 'guest', cameras: [], commit: 'c', limits: { script: 1, waypoints: 1, chat: 1 } }));
    FakeWS.instances.at(-1)!.drop();
    vi.advanceTimersByTime(1000);
    expect(onWakeNeeded).not.toHaveBeenCalled();
    expect(FakeWS.instances).toHaveLength(4);
  });

  it('open then close without a welcome still counts as a failure', () => {
    const onWakeNeeded = vi.fn();
    new Gateway('ws://x/ws', hello, { WebSocketImpl: WS, onWakeNeeded });
    for (let i = 0; i < 3; i++) {
      FakeWS.instances.at(-1)!.open();
      FakeWS.instances.at(-1)!.drop();
      vi.advanceTimersByTime(20000);
    }
    expect(onWakeNeeded).toHaveBeenCalledTimes(1);
  });

  it('queues sends until hello is sent (cap 20) and flushes in order', async () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    const ws = FakeWS.instances[0]!;
    ws.open();
    for (let i = 0; i < 25; i++) g.send({ t: 'chat', text: String(i) });
    expect(ws.sent).toHaveLength(0);
    await Promise.resolve(); await Promise.resolve();
    expect(ws.sent).toHaveLength(21);
    expect(JSON.parse(ws.sent[0]!).t).toBe('hello');
    expect(JSON.parse(ws.sent[1]!).text).toBe('0');
  });

  it('reconnect() closes a live socket first; a bad text frame clears the image header', () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    const onImage = vi.fn();
    g.onImage(onImage);
    const ws = FakeWS.instances[0]!;
    ws.open();
    ws.msg(JSON.stringify(imageHeader));
    ws.msg('garbage');
    ws.msg(new ArrayBuffer(2));
    expect(onImage).not.toHaveBeenCalled();
    g.reconnect();
    expect(ws.closed).toBe(true);
    expect(FakeWS.instances).toHaveLength(2);
  });

  it('close() from inside onClose stops the retry too', () => {
    let g: Gateway | null = null;
    g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS, onClose: () => g?.close() });
    FakeWS.instances[0]!.open();
    FakeWS.instances[0]!.drop();
    vi.advanceTimersByTime(60_000);
    expect(FakeWS.instances).toHaveLength(1);
  });

  it('close() stops reconnecting', () => {
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS });
    g.close();
    FakeWS.instances[0]!.drop();
    vi.advanceTimersByTime(60000);
    expect(FakeWS.instances).toHaveLength(1);
    expect(g.state).toBe('closed');
  });
});

describe('buildHello', () => {
  it('guest, signed-in and dev', () => {
    expect(buildHello({ nick: 'a', proof: { turnstile: 't' }, config: {} })).toEqual({ t: 'hello', nick: 'a', turnstile: 't' });
    expect(buildHello({ nick: 'a', proof: { idToken: 'j' }, config: {} })).toEqual({ t: 'hello', nick: 'a', idToken: 'j' });
    const dev = { sub: 'd', groups: ['admin'] };
    expect(buildHello({ nick: 'a', proof: {}, config: { dev: true }, devIdentity: dev })).toEqual({ t: 'hello', nick: 'a', dev });
    expect(buildHello({ nick: 'a', proof: { turnstile: 't' }, config: {}, devIdentity: dev }).dev).toBeUndefined();
  });
});

describe('Gateway onClose', () => {
  it('reports each lost connection, but not an explicit close()', () => {
    const onClose = vi.fn();
    const g = new Gateway('ws://x/ws', hello, { WebSocketImpl: WS, onClose });
    FakeWS.instances[0]!.open();
    FakeWS.instances[0]!.drop();
    expect(onClose).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(backoffDelay(1));
    FakeWS.instances[1]!.open();
    g.close();
    FakeWS.instances[1]!.drop();
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
