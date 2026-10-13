import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ensureAwake, type WakeState } from '../src/net/wake';

type Route = (url: string, init?: RequestInit) => Response | Promise<Response>;
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

function fakeFetch(route: Route) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const f = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    calls.push(init ? { url, init } : { url });
    return route(url, init);
  }) as typeof fetch;
  return { f, calls };
}

const healthzOk = () => json({ ok: true, sessions: 0, commit: 'abc', lab: 'ready' });
const proof = () => Promise.resolve({ turnstile: 'T' });

beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { vi.useRealTimers(); });

describe('ensureAwake', () => {
  it('returns running at once when running and healthy', async () => {
    const { f, calls } = fakeFetch(() => json({ state: 'running', healthy: true, commit: 'abc' }));
    expect(await ensureAwake(f, proof, () => {})).toEqual({ kind: 'running' });
    expect(calls).toHaveLength(1);
  });

  it('stopped: wakes, then running after 3 healthz polls', async () => {
    let polls = 0;
    const { f, calls } = fakeFetch((url) => {
      if (url === '/api/status') return json({ state: 'stopped', healthy: false, commit: null });
      if (url === '/api/wake') return json({}, 202);
      polls++;
      return polls < 3 ? json({ ok: false }, 503) : healthzOk();
    });
    const seen: WakeState[] = [];
    const p = ensureAwake(f, proof, (s) => seen.push(s));
    await vi.advanceTimersByTimeAsync(10_000);
    expect(await p).toEqual({ kind: 'running' });
    const wake = calls.find((c) => c.url === '/api/wake')!;
    expect(wake.init?.method).toBe('POST');
    expect(JSON.parse(wake.init?.body as string)).toEqual({ turnstile: 'T' });
    expect(polls).toBe(3);
    expect(seen[0]?.kind).toBe('starting');
    expect(seen.at(-1)).toEqual({ kind: 'running' });
  });

  it('a gateway whose lab is still starting is not running yet', async () => {
    let polls = 0;
    const { f } = fakeFetch((url) => {
      if (url === '/api/status') return json({ state: 'running', healthy: false, commit: 'a' });
      polls++;
      if (polls === 1) return json({ ok: true, sessions: 0, commit: 'a', lab: 'starting' });
      if (polls === 2) return json({ ok: true, sessions: 0, commit: 'a' }); // no lab field: not ready either
      return healthzOk();
    });
    const seen: WakeState[] = [];
    const p = ensureAwake(f, proof, (s) => seen.push(s));
    await vi.advanceTimersByTimeAsync(10_000);
    expect(await p).toEqual({ kind: 'running' });
    expect(polls).toBe(3);
    expect(seen.filter((s) => s.kind === 'running')).toHaveLength(1);
  });

  it('sends the idToken when that is the proof', async () => {
    const { f, calls } = fakeFetch((url) => {
      if (url === '/api/status') return json({ state: 'stopped', healthy: false, commit: null });
      if (url === '/api/wake') return json({}, 202);
      return healthzOk();
    });
    await ensureAwake(f, () => Promise.resolve({ idToken: 'J' }), () => {});
    expect(JSON.parse(calls.find((c) => c.url === '/api/wake')!.init?.body as string)).toEqual({ idToken: 'J' });
  });

  it('429 cap gives capped', async () => {
    const { f } = fakeFetch((url) => url === '/api/status'
      ? json({ state: 'stopped', healthy: false, commit: null }) : json({ reason: 'cap' }, 429));
    expect(await ensureAwake(f, proof, () => {})).toEqual({ kind: 'capped' });
  });

  it('running but unhealthy polls healthz', async () => {
    let n = 0;
    const { f } = fakeFetch((url) => {
      if (url === '/api/status') return json({ state: 'running', healthy: false, commit: 'a' });
      return ++n < 2 ? json({ ok: false }, 502) : healthzOk();
    });
    const p = ensureAwake(f, proof, () => {});
    await vi.advanceTimersByTimeAsync(5000);
    expect(await p).toEqual({ kind: 'running' });
  });

  it('stopping: polls status until stopped, then wakes', async () => {
    const states = ['stopping', 'stopping', 'stopped'];
    let woke = false;
    const { f } = fakeFetch((url) => {
      if (url === '/api/status') return json({ state: states.shift() ?? 'stopped', healthy: false, commit: null });
      if (url === '/api/wake') { woke = true; return json({}, 202); }
      return healthzOk();
    });
    const p = ensureAwake(f, proof, () => {});
    await vi.advanceTimersByTimeAsync(10_000);
    expect(await p).toEqual({ kind: 'running' });
    expect(woke).toBe(true);
  });

  it('409 stopping from wake retries after a delay', async () => {
    let wakes = 0;
    const { f } = fakeFetch((url) => {
      if (url === '/api/status') return json({ state: 'stopped', healthy: false, commit: null });
      if (url === '/api/wake') return ++wakes === 1 ? json({ reason: 'stopping' }, 409) : json({}, 202);
      return healthzOk();
    });
    const p = ensureAwake(f, proof, () => {});
    await vi.advanceTimersByTimeAsync(10_000);
    expect(await p).toEqual({ kind: 'running' });
    expect(wakes).toBe(2);
  });

  it('401 retries once with a fresh proof, then errors', async () => {
    const getProof = vi.fn(proof);
    const { f } = fakeFetch((url) => url === '/api/status'
      ? json({ state: 'stopped', healthy: false, commit: null }) : json({}, 401));
    const r = await ensureAwake(f, getProof, () => {});
    expect(r.kind).toBe('error');
    expect(getProof).toHaveBeenCalledTimes(2);
  });

  it('401 then 202 succeeds', async () => {
    let wakes = 0;
    const { f } = fakeFetch((url) => {
      if (url === '/api/status') return json({ state: 'stopped', healthy: false, commit: null });
      if (url === '/api/wake') return ++wakes === 1 ? json({}, 401) : json({}, 202);
      return healthzOk();
    });
    const p = ensureAwake(f, proof, () => {});
    await vi.advanceTimersByTimeAsync(5000);
    expect(await p).toEqual({ kind: 'running' });
  });

  it('400 and 403 are errors', async () => {
    for (const status of [400, 403]) {
      const { f } = fakeFetch((url) => url === '/api/status'
        ? json({ state: 'stopped', healthy: false, commit: null }) : json({}, status));
      expect((await ensureAwake(f, proof, () => {})).kind).toBe('error');
    }
  });

  it('times out at 180 s with an error', async () => {
    const { f } = fakeFetch((url) => url === '/api/status'
      ? json({ state: 'starting', healthy: false, commit: null }) : json({ ok: false }, 503));
    const seen: WakeState[] = [];
    const p = ensureAwake(f, proof, (s) => seen.push(s));
    await vi.advanceTimersByTimeAsync(181_000);
    expect((await p).kind).toBe('error');
    expect(seen.at(-1)?.kind).toBe('error');
  });

  it('a getProof that never resolves ends in error at 180 s', async () => {
    const { f } = fakeFetch(() => json({ state: 'stopped', healthy: false, commit: null }));
    const p = ensureAwake(f, () => new Promise(() => {}), () => {});
    await vi.advanceTimersByTimeAsync(181_000);
    expect((await p).kind).toBe('error');
  });

  it('a hanging status fetch does not block past 180 s', async () => {
    const f = (() => new Promise<Response>(() => {})) as unknown as typeof fetch;
    const p = ensureAwake(f, proof, () => {});
    await vi.advanceTimersByTimeAsync(200_000);
    expect((await p).kind).toBe('error');
  });

  it('passes fakeStatus through to the status URL', async () => {
    vi.stubGlobal('location', { search: '?fakeStatus=stopped' });
    try {
      const { f, calls } = fakeFetch(() => json({ state: 'running', healthy: true, commit: null }));
      await ensureAwake(f, proof, () => {});
      expect(calls[0]?.url).toBe('/api/status?fakeStatus=stopped');
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
