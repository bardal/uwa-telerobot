export type State = "stopped" | "starting" | "running" | "stopping";

export interface Deps {
  ec2: {
    /** Raw EC2 state name: pending, running, stopping, stopped, shutting-down, terminated. */
    describeState(instanceId: string): Promise<string>;
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
  const state = mapState(await deps.ec2.describeState(deps.instanceId));
  const hb = await readHeartbeat(deps);
  const healthy = hb !== null && deps.now() - hb.time <= HEALTHY_MS;
  return { state, healthy, commit: hb?.commit ?? null };
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
  if (header(headers, "x-telerobot-origin") !== deps.originSecret) {
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

  const state = mapState(await deps.ec2.describeState(deps.instanceId));
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
  const state = mapState(await deps.ec2.describeState(deps.instanceId));
  if (state !== "running") return;

  const now = deps.now();
  const param = minutesParam(now);
  const current = await readMinutes(deps);
  await deps.ssm.put(param, String(current + REAP_MINUTES));

  const hb = await readHeartbeat(deps);
  const stale = hb === null || now - hb.time > STALE_MS;
  const idle = hb !== null && hb.sessions === 0 && now - hb.lastSessionEnd > IDLE_MS;
  if (stale || idle) await deps.ec2.stop(deps.instanceId);
}
