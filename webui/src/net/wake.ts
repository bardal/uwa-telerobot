export type WakeState =
  | { kind: 'running' }
  /**
   * computer: the lab instance is starting and its gateway cannot be reached yet
   * (including CloudFront catching up with the instance's new address);
   * robot: the gateway answers and its lab (MOM, the robot, the cameras) is starting.
   */
  | { kind: 'starting'; since: number; step: WakeStep }
  | { kind: 'capped' }
  | { kind: 'error'; message: string };

export type WakeStep = 'computer' | 'robot';

export const POLL_MS = 2000;
export const STOPPING_RETRY_MS = 3000;
export const WAKE_TIMEOUT_MS = 300_000;

type Proof = { turnstile?: string; idToken?: string };
type StatusBody = { state: 'stopped' | 'starting' | 'running' | 'stopping'; healthy: boolean; commit: string | null };

export const FETCH_TIMEOUT_MS = 10_000;

/** Rejects if p does not settle within ms (also covers fetches that ignore their signal). */
function within<T>(p: Promise<T>, ms: number, what: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`${what} timed out`)), ms);
    p.then((v) => { clearTimeout(t); resolve(v); }, (e: unknown) => { clearTimeout(t); reject(e instanceof Error ? e : new Error(String(e))); });
  });
}

function timedFetch(fetchImpl: typeof fetch, url: string, init: RequestInit = {}): Promise<Response> {
  return within(fetchImpl(url, { ...init, signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) }), FETCH_TIMEOUT_MS, url);
}

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

function statusUrl(): string {
  // In dev the gateway can fake a stopped lab: ?fakeStatus=stopped on the page is passed through.
  try {
    const fake = new URLSearchParams(globalThis.location?.search ?? '').get('fakeStatus');
    if (fake) return `/api/status?fakeStatus=${encodeURIComponent(fake)}`;
  } catch { /* no location */ }
  return '/api/status';
}

async function getStatus(fetchImpl: typeof fetch): Promise<StatusBody | null> {
  try {
    const res = await timedFetch(fetchImpl, statusUrl(), { cache: 'no-store' });
    if (!res.ok) return null;
    const b = (await res.json()) as Partial<StatusBody>;
    if (b.state !== 'stopped' && b.state !== 'starting' && b.state !== 'running' && b.state !== 'stopping') return null;
    return { state: b.state, healthy: b.healthy === true, commit: b.commit ?? null };
  } catch {
    return null;
  }
}

/**
 * ready: the gateway is up and its lab is ready for sessions (MOM, the robot proxy
 * and the cameras); robot: the gateway answers but its lab is still starting;
 * computer: no answer from the gateway.
 */
async function health(fetchImpl: typeof fetch): Promise<'ready' | WakeStep> {
  try {
    const res = await timedFetch(fetchImpl, '/healthz', { cache: 'no-store' });
    if (!res.ok) return 'computer';
    const b = (await res.json()) as { ok?: unknown; lab?: unknown };
    if (b.ok !== true) return 'computer';
    return b.lab === 'ready' ? 'ready' : 'robot';
  } catch {
    return 'computer';
  }
}

type WakeResult = 'started' | 'stopping' | WakeState;

async function postWake(fetchImpl: typeof fetch, getProof: () => Promise<Proof>, budgetMs: () => number): Promise<WakeResult> {
  for (let attempt = 0; attempt < 2; attempt++) {
    let proof: Proof;
    try {
      proof = await within(getProof(), Math.max(1, budgetMs()), 'Proof of humanity');
    } catch (e) {
      return { kind: 'error', message: `Could not get proof of humanity: ${e instanceof Error ? e.message : String(e)}` };
    }
    const body: Proof = proof.idToken ? { idToken: proof.idToken } : { turnstile: proof.turnstile };
    let res: Response;
    try {
      res = await timedFetch(fetchImpl, '/api/wake', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
      });
    } catch (e) {
      return { kind: 'error', message: `Wake request failed: ${e instanceof Error ? e.message : String(e)}` };
    }
    if (res.status === 202) return 'started';
    if (res.status === 409) return 'stopping';
    if (res.status === 429) return { kind: 'capped' };
    if (res.status === 401 && attempt === 0) continue; // fresh proof, once
    return { kind: 'error', message: `Wake refused (HTTP ${res.status})` };
  }
  return { kind: 'error', message: 'Wake refused' };
}

/**
 * Makes sure the lab instance is up. Resolves with the final state; progress is reported
 * through onProgress ("starting" with the start time, then the final state).
 */
export async function ensureAwake(
  fetchImpl: typeof fetch,
  getProof: () => Promise<Proof>,
  onProgress: (s: WakeState) => void,
  now: () => number = Date.now,
): Promise<WakeState> {
  const start = now();
  const finish = (s: WakeState): WakeState => { onProgress(s); return s; };
  const timedOut = () => now() - start >= WAKE_TIMEOUT_MS;
  const timeout = (): WakeState => finish({ kind: 'error', message: 'The robot did not wake up in time' });
  let announced: WakeStep | null = null;
  // reports each step once; never back from robot to computer (a dropped poll is not news)
  const starting = (step: WakeStep = 'computer') => {
    if (announced === step || (announced === 'robot' && step === 'computer')) return;
    announced = step;
    onProgress({ kind: 'starting', since: start, step });
  };

  let waking = false; // wake POSTed (or lab starting): poll /healthz
  while (!timedOut()) {
    if (waking) {
      starting();
      const h = await health(fetchImpl);
      if (h === 'ready') return finish({ kind: 'running' });
      starting(h);
      await sleep(POLL_MS);
      continue;
    }
    const st = await getStatus(fetchImpl);
    if (!st) {
      await sleep(POLL_MS);
      continue;
    }
    if (st.state === 'running' && st.healthy) return finish({ kind: 'running' });
    if (st.state === 'running' || st.state === 'starting') {
      waking = true;
      continue;
    }
    starting();
    if (st.state === 'stopping') {
      await sleep(POLL_MS);
      continue;
    }
    // stopped
    const r = await postWake(fetchImpl, getProof, () => WAKE_TIMEOUT_MS - (now() - start));
    if (r === 'started') { waking = true; continue; }
    if (r === 'stopping') { await sleep(STOPPING_RETRY_MS); continue; }
    return finish(r);
  }
  return timeout();
}
