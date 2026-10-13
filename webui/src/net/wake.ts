export type WakeState =
  | { kind: 'running' }
  | { kind: 'starting'; since: number }
  | { kind: 'capped' }
  | { kind: 'error'; message: string };

export const POLL_MS = 2000;
export const STOPPING_RETRY_MS = 3000;
export const WAKE_TIMEOUT_MS = 180_000;

type Proof = { turnstile?: string; idToken?: string };
type StatusBody = { state: 'stopped' | 'starting' | 'running' | 'stopping'; healthy: boolean; commit: string | null };

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
    const res = await fetchImpl(statusUrl(), { cache: 'no-store' });
    if (!res.ok) return null;
    const b = (await res.json()) as Partial<StatusBody>;
    if (b.state !== 'stopped' && b.state !== 'starting' && b.state !== 'running' && b.state !== 'stopping') return null;
    return { state: b.state, healthy: b.healthy === true, commit: b.commit ?? null };
  } catch {
    return null;
  }
}

async function healthy(fetchImpl: typeof fetch): Promise<boolean> {
  try {
    const res = await fetchImpl('/healthz', { cache: 'no-store' });
    if (!res.ok) return false;
    const b = (await res.json()) as { ok?: unknown };
    return b.ok === true;
  } catch {
    return false;
  }
}

type WakeResult = 'started' | 'stopping' | WakeState;

async function postWake(fetchImpl: typeof fetch, getProof: () => Promise<Proof>): Promise<WakeResult> {
  for (let attempt = 0; attempt < 2; attempt++) {
    let proof: Proof;
    try {
      proof = await getProof();
    } catch (e) {
      return { kind: 'error', message: `Could not get proof of humanity: ${e instanceof Error ? e.message : String(e)}` };
    }
    const body: Proof = proof.idToken ? { idToken: proof.idToken } : { turnstile: proof.turnstile };
    let res: Response;
    try {
      res = await fetchImpl('/api/wake', {
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
  let announced = false;
  const starting = () => {
    if (!announced) { announced = true; onProgress({ kind: 'starting', since: start }); }
  };

  let waking = false; // wake POSTed (or lab starting): poll /healthz
  while (!timedOut()) {
    if (waking) {
      starting();
      if (await healthy(fetchImpl)) return finish({ kind: 'running' });
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
    const r = await postWake(fetchImpl, getProof);
    if (r === 'started') { waking = true; continue; }
    if (r === 'stopping') { await sleep(STOPPING_RETRY_MS); continue; }
    return finish(r);
  }
  return timeout();
}
