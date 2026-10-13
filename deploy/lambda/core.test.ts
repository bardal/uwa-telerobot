import { describe, expect, it, vi } from "vitest";
import { reap, status, wake, type Deps } from "./core";

const NOW = Date.UTC(2026, 9, 13, 12, 0, 0);
const SECRET = "s3cret";
const GOOD = { "x-telerobot-origin": SECRET };

function makeDeps(opts: {
  state?: string;
  heartbeat?: object | null;
  minutes?: Record<string, string>;
  turnstileOk?: boolean;
  idTokenOk?: boolean;
  now?: number;
  hoursCap?: number;
}) {
  const store: Record<string, string> = { ...(opts.minutes ?? {}) };
  if (opts.heartbeat) store["/telerobot/heartbeat"] = JSON.stringify(opts.heartbeat);
  const ec2 = {
    describeState: vi.fn(async () => opts.state ?? "stopped"),
    start: vi.fn(async () => {}),
    stop: vi.fn(async () => {}),
  };
  const ssm = {
    get: vi.fn(async (n: string) => store[n] ?? null),
    put: vi.fn(async (n: string, v: string) => {
      store[n] = v;
    }),
  };
  const deps: Deps = {
    ec2,
    ssm,
    verifyTurnstile: vi.fn(async () => opts.turnstileOk ?? true),
    verifyIdToken: vi.fn(async () => opts.idTokenOk ?? true),
    now: () => opts.now ?? NOW,
    instanceId: "i-123",
    hoursCap: opts.hoursCap ?? 150,
    originSecret: SECRET,
  };
  return { deps, ec2, ssm, store };
}

describe("status", () => {
  it("maps pending to starting", async () => {
    const { deps } = makeDeps({ state: "pending" });
    expect((await status(deps)).state).toBe("starting");
  });
  it("maps stopping, shutting-down, stopped, running", async () => {
    for (const [raw, want] of [
      ["stopping", "stopping"],
      ["shutting-down", "stopping"],
      ["stopped", "stopped"],
      ["running", "running"],
    ] as const) {
      const { deps } = makeDeps({ state: raw });
      expect((await status(deps)).state).toBe(want);
    }
  });
  it("healthy when heartbeat within 90s, with commit", async () => {
    const { deps } = makeDeps({
      state: "running",
      heartbeat: { time: NOW - 90_000, sessions: 0, lastSessionEnd: 0, commit: "abc" },
    });
    expect(await status(deps)).toEqual({ state: "running", healthy: true, commit: "abc" });
  });
  it("unhealthy when heartbeat older than 90s", async () => {
    const { deps } = makeDeps({
      state: "running",
      heartbeat: { time: NOW - 91_000, sessions: 0, lastSessionEnd: 0, commit: "abc" },
    });
    expect((await status(deps)).healthy).toBe(false);
  });
  it("no heartbeat: unhealthy, null commit", async () => {
    const { deps } = makeDeps({ state: "stopped" });
    expect(await status(deps)).toEqual({ state: "stopped", healthy: false, commit: null });
  });
  it("malformed heartbeat is treated as absent", async () => {
    const { deps, store } = makeDeps({ state: "running" });
    store["/telerobot/heartbeat"] = "not json";
    expect(await status(deps)).toEqual({ state: "running", healthy: false, commit: null });
  });
});

describe("wake", () => {
  it("403 on wrong or missing origin header", async () => {
    const { deps, ec2 } = makeDeps({});
    expect((await wake(deps, '{"turnstile":"t"}', { "x-telerobot-origin": "nope" })).status).toBe(403);
    expect((await wake(deps, '{"turnstile":"t"}', {})).status).toBe(403);
    expect(ec2.start).not.toHaveBeenCalled();
  });
  it("400 on invalid bodies", async () => {
    const { deps } = makeDeps({});
    for (const body of [undefined, "", "nope", "[]", "{}", '{"turnstile":5}', '{"idToken":""}']) {
      expect((await wake(deps, body, GOOD)).status).toBe(400);
    }
  });
  it("401 when turnstile fails", async () => {
    const { deps, ec2 } = makeDeps({ turnstileOk: false });
    expect((await wake(deps, '{"turnstile":"t"}', GOOD)).status).toBe(401);
    expect(ec2.start).not.toHaveBeenCalled();
  });
  it("401 when id token fails", async () => {
    const { deps } = makeDeps({ idTokenOk: false });
    expect((await wake(deps, '{"idToken":"x"}', GOOD)).status).toBe(401);
  });
  it("401 when verifier throws", async () => {
    const { deps } = makeDeps({});
    deps.verifyIdToken = async () => {
      throw new Error("bad");
    };
    expect((await wake(deps, '{"idToken":"x"}', GOOD)).status).toBe(401);
  });
  it("202 and starts the instance with turnstile proof", async () => {
    const { deps, ec2 } = makeDeps({});
    const r = await wake(deps, '{"turnstile":"t"}', GOOD);
    expect(r.status).toBe(202);
    expect(ec2.start).toHaveBeenCalledWith("i-123");
    expect(deps.verifyTurnstile).toHaveBeenCalledWith("t");
  });
  it("202 and starts with id token proof", async () => {
    const { deps, ec2 } = makeDeps({});
    expect((await wake(deps, '{"idToken":"x"}', GOOD)).status).toBe(202);
    expect(ec2.start).toHaveBeenCalled();
  });
  it("accepts mixed-case header names", async () => {
    const { deps } = makeDeps({});
    expect((await wake(deps, '{"turnstile":"t"}', { "X-Telerobot-Origin": SECRET })).status).toBe(202);
  });
  it("429 cap when minutes at the cap", async () => {
    const { deps, ec2 } = makeDeps({ minutes: { "/telerobot/minutes/2026-10": "9000" } });
    const r = await wake(deps, '{"turnstile":"t"}', GOOD);
    expect(r).toEqual({ status: 429, body: { reason: "cap" } });
    expect(ec2.start).not.toHaveBeenCalled();
  });
  it("allowed just under the cap", async () => {
    const { deps } = makeDeps({ minutes: { "/telerobot/minutes/2026-10": "8999" } });
    expect((await wake(deps, '{"turnstile":"t"}', GOOD)).status).toBe(202);
  });
  it("last month's minutes do not count", async () => {
    const { deps } = makeDeps({ minutes: { "/telerobot/minutes/2026-09": "99999" } });
    expect((await wake(deps, '{"turnstile":"t"}', GOOD)).status).toBe(202);
  });
  it("already running is a no-op 202", async () => {
    const { deps, ec2 } = makeDeps({ state: "running" });
    expect((await wake(deps, '{"turnstile":"t"}', GOOD)).status).toBe(202);
    expect(ec2.start).not.toHaveBeenCalled();
  });
  it("pending is a no-op 202", async () => {
    const { deps, ec2 } = makeDeps({ state: "pending" });
    expect((await wake(deps, '{"turnstile":"t"}', GOOD)).status).toBe(202);
    expect(ec2.start).not.toHaveBeenCalled();
  });
});

describe("reap", () => {
  const fresh = (o: object = {}) => ({
    time: NOW - 30_000,
    sessions: 1,
    lastSessionEnd: 0,
    commit: "abc",
    ...o,
  });

  it("stopped instance does not count hours", async () => {
    const { deps, ssm, ec2 } = makeDeps({ state: "stopped" });
    await reap(deps);
    expect(ssm.put).not.toHaveBeenCalled();
    expect(ec2.stop).not.toHaveBeenCalled();
  });
  it("running adds 15 minutes", async () => {
    const { deps, store } = makeDeps({
      state: "running",
      heartbeat: fresh(),
      minutes: { "/telerobot/minutes/2026-10": "30" },
    });
    await reap(deps);
    expect(store["/telerobot/minutes/2026-10"]).toBe("45");
  });
  it("starts the counter from zero", async () => {
    const { deps, store } = makeDeps({ state: "running", heartbeat: fresh() });
    await reap(deps);
    expect(store["/telerobot/minutes/2026-10"]).toBe("15");
  });
  it("rolls over at month change (UTC)", async () => {
    const now = Date.UTC(2026, 10, 1, 0, 5, 0);
    const { deps, store } = makeDeps({
      state: "running",
      now,
      heartbeat: fresh({ time: now - 1000 }),
      minutes: { "/telerobot/minutes/2026-10": "8000" },
    });
    await reap(deps);
    expect(store["/telerobot/minutes/2026-11"]).toBe("15");
    expect(store["/telerobot/minutes/2026-10"]).toBe("8000");
  });
  it("does not stop an active instance", async () => {
    const { deps, ec2 } = makeDeps({ state: "running", heartbeat: fresh() });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
  });
  it("stops on stale heartbeat (over 5 min)", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      heartbeat: fresh({ time: NOW - 5 * 60_000 - 1 }),
    });
    await reap(deps);
    expect(ec2.stop).toHaveBeenCalledWith("i-123");
  });
  it("stops on missing heartbeat", async () => {
    const { deps, ec2 } = makeDeps({ state: "running" });
    await reap(deps);
    expect(ec2.stop).toHaveBeenCalled();
  });
  it("does not stop with heartbeat exactly 5 min old and active session", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      heartbeat: fresh({ time: NOW - 5 * 60_000 }),
    });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
  });
  it("stops when idle: no sessions, last end over 15 min ago", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      heartbeat: fresh({ sessions: 0, lastSessionEnd: NOW - 15 * 60_000 - 1 }),
    });
    await reap(deps);
    expect(ec2.stop).toHaveBeenCalled();
  });
  it("stops when idle with lastSessionEnd 0 (never had a session)", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      heartbeat: fresh({ sessions: 0, lastSessionEnd: 0 }),
    });
    await reap(deps);
    expect(ec2.stop).toHaveBeenCalled();
  });
  it("keeps running when idle for under 15 min", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      heartbeat: fresh({ sessions: 0, lastSessionEnd: NOW - 10 * 60_000 }),
    });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
  });
  it("keeps running when sessions are active even if lastSessionEnd is old", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      heartbeat: fresh({ sessions: 2, lastSessionEnd: NOW - 3600_000 }),
    });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
  });
});
