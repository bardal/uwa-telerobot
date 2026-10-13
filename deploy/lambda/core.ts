import { timingSafeEqual } from "node:crypto";

export type State = "stopped" | "starting" | "running" | "stopping";

export interface Deps {
  ec2: {
    /** Raw EC2 state name: pending, running, stopping, stopped, shutting-down, terminated. */
    describeState(instanceId: string): Promise<{ state: string; launchTime: number }>;
    start(instanceId: string): Promise<void>;
    stop(instanceId: string): Promise<void>;
  };
  ssm: {
    get(name: string): Promise<string | null>;
    put(name: string, value: string): Promise<void>;
  };
  verifyTurnstile(token: string): Promise<boolean>;
  verifyIdToken(token: string): Promise<boolean>;
  now(): number;
  instanceId: string;
  hoursCap: number;
  originSecret: string;
}

export interface StatusResult {
  state: State;
  healthy: boolean;
  commit: string | null;
}

export interface HttpResult {
  status: number;
  body: unknown;
}

interface Heartbeat {
  time: number;
  sessions: number;
  lastSessionEnd: number;
  commit: string | null;
}

export const HEARTBEAT_PARAM = "/telerobot/heartbeat";
const HEALTHY_MS = 90_000;
const STALE_MS = 5 * 60_000;
const IDLE_MS = 15 * 60_000;
const REAP_MINUTES = 15;
const BOOT_GRACE_MS = 10 * 60_000;

export function minutesParam(nowMs: number): string {
  const d = new Date(nowMs);
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  return `/telerobot/minutes/${d.getUTCFullYear()}-${m}`;
}

function mapState(raw: string): State {
  switch (raw) {
    case "running":
      return "running";
    case "pending":
      return "starting";
    case "stopping":
    case "shutting-down":
      return "stopping";
    default:
      return "stopped";
  }
}

async function readHeartbeat(deps: Deps): Promise<Heartbeat | null> {
  const raw = await deps.ssm.get(HEARTBEAT_PARAM);
  if (!raw) return null;
  try {
    const j = JSON.parse(raw);
    if (!j || typeof j !== "object" || typeof j.time !== "number") return null;
    return {
      time: j.time,
      sessions: typeof j.sessions === "number" ? j.sessions : 0,
      lastSessionEnd: typeof j.lastSessionEnd === "number" ? j.lastSessionEnd : 0,
      commit: typeof j.commit === "string" ? j.commit : null,
    };
  } catch {
    return null;
  }
}

async function readMinutes(deps: Deps): Promise<number> {
  const n = parseInt((await deps.ssm.get(minutesParam(deps.now()))) ?? "0", 10);
  return Number.isFinite(n) ? n : 0;
}

export async function status(deps: Deps): Promise<StatusResult> {
  const state = mapState((await deps.ec2.describeState(deps.instanceId)).state);
  const hb = await readHeartbeat(deps);
  const healthy = state === "running" && hb !== null && deps.now() - hb.time <= HEALTHY_MS;
  return { state, healthy, commit: hb?.commit ?? null };
}

/** How long a warm container reuses a computed /api/status (spec). */
export const STATUS_CACHE_MS = 2_000;

/**
 * /api/status memoised for STATUS_CACHE_MS on deps.now(), so a burst of
 * polls (every waking page polls) costs one EC2 and one SSM call per 2 s per
 * warm container instead of queueing at reserved concurrency 2. Failures are
 * never cached; invalidate() after a wake so the next poll sees "starting".
 */
export function cachedStatus(ttlMs = STATUS_CACHE_MS) {
  let cached: { at: number; value: StatusResult } | null = null;
  return {
    async get(deps: Deps): Promise<StatusResult> {
      const now = deps.now();
      if (cached && now >= cached.at && now - cached.at < ttlMs) return { ...cached.value };
      const value = await status(deps);
      cached = { at: now, value };
      return { ...value };
    },
    invalidate(): void {
      cached = null;
    },
  };
}

export function checkOrigin(
  deps: Pick<Deps, "originSecret">,
  headers: Record<string, string | undefined>,
): boolean {
  const got = header(headers, "x-telerobot-origin");
  if (!deps.originSecret || got === undefined) return false;
  const a = Buffer.from(got);
  const b = Buffer.from(deps.originSecret);
  return a.length === b.length && timingSafeEqual(a, b);
}

function header(headers: Record<string, string | undefined>, name: string): string | undefined {
  for (const [k, v] of Object.entries(headers)) {
    if (k.toLowerCase() === name) return v;
  }
  return undefined;
}

export async function wake(
  deps: Deps,
  rawBody: string | undefined | null,
  headers: Record<string, string | undefined>,
): Promise<HttpResult> {
  if (!checkOrigin(deps, headers)) {
    return { status: 403, body: { error: "forbidden" } };
  }

  let body: unknown;
  try {
    body = JSON.parse(rawBody ?? "");
  } catch {
    return { status: 400, body: { error: "invalid body" } };
  }
  const b = body as { turnstile?: unknown; idToken?: unknown } | null;
  const turnstile = b && typeof b === "object" ? b.turnstile : undefined;
  const idToken = b && typeof b === "object" ? b.idToken : undefined;
  const hasTurnstile = typeof turnstile === "string" && turnstile.length > 0;
  const hasIdToken = typeof idToken === "string" && idToken.length > 0;
  if (hasTurnstile === hasIdToken) {
    return { status: 400, body: { error: "invalid body" } };
  }

  let ok = false;
  try {
    ok = hasTurnstile
      ? await deps.verifyTurnstile(turnstile as string)
      : await deps.verifyIdToken(idToken as string);
  } catch {
    ok = false;
  }
  if (!ok) return { status: 401, body: { error: "unauthorized" } };

  const state = mapState((await deps.ec2.describeState(deps.instanceId)).state);
  if (state === "running" || state === "starting") {
    return { status: 202, body: { state } };
  }

  if ((await readMinutes(deps)) >= deps.hoursCap * 60) {
    return { status: 429, body: { reason: "cap" } };
  }

  if (state === "stopping") {
    // EC2 refuses StartInstances until the stop completes; the UI can retry.
    return { status: 409, body: { reason: "stopping" } };
  }

  await deps.ec2.start(deps.instanceId);
  return { status: 202, body: { state: "starting" } };
}

export async function reap(deps: Deps): Promise<void> {
  const { state: raw, launchTime } = await deps.ec2.describeState(deps.instanceId);
  if (mapState(raw) !== "running") return;

  const now = deps.now();
  const total = (await readMinutes(deps)) + REAP_MINUTES;
  await deps.ssm.put(minutesParam(now), String(total));

  // Hard cap: stop a running instance once the month's minutes reach the cap.
  if (total >= deps.hoursCap * 60) {
    await deps.ec2.stop(deps.instanceId);
    return;
  }

  // Boot grace: never stop an instance launched recently.
  if (now - launchTime < BOOT_GRACE_MS) return;

  // A heartbeat older than LaunchTime belongs to the previous run.
  let hb = await readHeartbeat(deps);
  if (hb !== null && hb.time < launchTime) hb = null;

  const stale = hb === null || now - hb.time > STALE_MS;
  const idle =
    hb !== null && hb.sessions === 0 && now - Math.max(hb.lastSessionEnd, launchTime) > IDLE_MS;
  if (stale || idle) await deps.ec2.stop(deps.instanceId);
}
