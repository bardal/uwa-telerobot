import { describe, expect, it, vi } from "vitest";
import { STATUS_CACHE_MS, cachedStatus, checkOrigin, reap, status, syncLabDns, wake, type Deps } from "./core";

const NOW = Date.UTC(2026, 9, 13, 12, 0, 0);
const HOUR = 3_600_000;
const SECRET = "s3cret";
const GOOD = { "x-telerobot-origin": SECRET };

function makeDeps(opts: {
  state?: string;
  launchTime?: number;
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
    describeState: vi.fn<Deps["ec2"]["describeState"]>(async () => ({
      state: opts.state ?? "stopped",
      launchTime: opts.launchTime ?? NOW - 60 * 60_000,
    })),
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

describe("cachedStatus", () => {
  // A fake clock: deps.now() reads `t`.
  function clocked(state = "running") {
    let t = NOW;
    const made = makeDeps({ state });
    const deps: Deps = { ...made.deps, now: () => t };
    return { ...made, deps, advance: (ms: number) => (t += ms) };
  }

  it("serves the same status for 2 s without asking EC2 or SSM again", async () => {
    const { deps, ec2, ssm, advance } = clocked();
    const cache = cachedStatus();
    const first = await cache.get(deps);
    advance(1_000);
    expect(await cache.get(deps)).toEqual(first);
    advance(999);
    expect(await cache.get(deps)).toEqual(first);
    expect(ec2.describeState).toHaveBeenCalledTimes(1);
    expect(ssm.get).toHaveBeenCalledTimes(1);
  });

  it("recomputes once 2 s have passed", async () => {
    const { deps, ec2, advance } = clocked();
    const cache = cachedStatus();
    await cache.get(deps);
    advance(2_000);
    await cache.get(deps);
    expect(ec2.describeState).toHaveBeenCalledTimes(2);
  });

  it("sees a state change after the 2 s", async () => {
    const { deps, ec2, advance } = clocked("stopped");
    const cache = cachedStatus();
    expect((await cache.get(deps)).state).toBe("stopped");
    ec2.describeState.mockResolvedValue({ state: "pending", launchTime: NOW });
    advance(500);
    expect((await cache.get(deps)).state).toBe("stopped");
    advance(1_500);
    expect((await cache.get(deps)).state).toBe("starting");
  });

  it("invalidate() forces the next call to recompute (after a wake)", async () => {
    const { deps, ec2 } = clocked();
    const cache = cachedStatus();
    await cache.get(deps);
    cache.invalidate();
    await cache.get(deps);
    expect(ec2.describeState).toHaveBeenCalledTimes(2);
  });

  it("does not cache a failure", async () => {
    const { deps, ec2 } = clocked();
    const cache = cachedStatus();
    ec2.describeState.mockRejectedValueOnce(new Error("throttled"));
    await expect(cache.get(deps)).rejects.toThrow("throttled");
    expect((await cache.get(deps)).state).toBe("running");
    expect(ec2.describeState).toHaveBeenCalledTimes(2);
  });

  it("recomputes if the clock goes backwards", async () => {
    const { deps, ec2, advance } = clocked();
    const cache = cachedStatus();
    await cache.get(deps);
    advance(-5_000);
    await cache.get(deps);
    expect(ec2.describeState).toHaveBeenCalledTimes(2);
  });

  it("defaults to the spec's 2 s", () => {
    expect(STATUS_CACHE_MS).toBe(2_000);
  });
});

describe("status healthy", () => {
  it("requires running state", async () => {
    const { deps } = makeDeps({
      state: "stopping",
      heartbeat: { time: NOW - 1000, sessions: 0, lastSessionEnd: 0, commit: "abc" },
    });
    expect((await status(deps)).healthy).toBe(false);
  });
});

describe("wake", () => {
  it("401 when the verifier throws (e.g. turnstile timeout)", async () => {
    const { deps, ec2 } = makeDeps({});
    deps.verifyTurnstile = async () => {
      throw new Error("timeout");
    };
    expect((await wake(deps, '{"turnstile":"t"}', GOOD)).status).toBe(401);
    expect(ec2.start).not.toHaveBeenCalled();
  });
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
  it("stops when idle since launch with lastSessionEnd 0 (no session since boot)", async () => {
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

  const MIN = 60_000;
  it("recently launched + missing heartbeat: not stopped, minutes still counted", async () => {
    const { deps, ec2, store } = makeDeps({ state: "running", launchTime: NOW - 2 * MIN });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
    expect(store["/telerobot/minutes/2026-10"]).toBe("15");
  });
  it("recently launched + leftover heartbeat from before launch: not stopped", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      launchTime: NOW - 3 * MIN,
      heartbeat: fresh({ time: NOW - 3 * HOUR, sessions: 0, lastSessionEnd: NOW - 3 * HOUR }),
    });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
  });
  it("launched 11 min ago + no heartbeat: stopped", async () => {
    const { deps, ec2 } = makeDeps({ state: "running", launchTime: NOW - 11 * MIN });
    await reap(deps);
    expect(ec2.stop).toHaveBeenCalled();
  });
  it("launched 11 min ago + only a pre-launch heartbeat: stopped", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      launchTime: NOW - 11 * MIN,
      heartbeat: fresh({ time: NOW - 12 * MIN }),
    });
    await reap(deps);
    expect(ec2.stop).toHaveBeenCalled();
  });
  it("launched 20 min ago, fresh heartbeat, sessions 0, lastSessionEnd 0: stopped (idle since launch)", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      launchTime: NOW - 20 * MIN,
      heartbeat: fresh({ sessions: 0, lastSessionEnd: 0 }),
    });
    await reap(deps);
    expect(ec2.stop).toHaveBeenCalled();
  });
  it("launched 20 min ago, sessions 0, lastSessionEnd 5 min ago: not stopped", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      launchTime: NOW - 20 * MIN,
      heartbeat: fresh({ sessions: 0, lastSessionEnd: NOW - 5 * MIN }),
    });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
  });
  it("lastSessionEnd from the previous run is ignored (idle measured from launch)", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      launchTime: NOW - 12 * MIN,
      heartbeat: fresh({ sessions: 0, lastSessionEnd: NOW - 60 * MIN }),
    });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
  });
  it("hard cap: stops a running instance once minutes reach the cap", async () => {
    const { deps, ec2, store } = makeDeps({
      state: "running",
      launchTime: NOW - 2 * MIN,
      heartbeat: fresh(),
      minutes: { "/telerobot/minutes/2026-10": "8985" },
    });
    await reap(deps);
    expect(store["/telerobot/minutes/2026-10"]).toBe("9000");
    expect(ec2.stop).toHaveBeenCalled();
  });
  it("under the cap after adding: not stopped", async () => {
    const { deps, ec2 } = makeDeps({
      state: "running",
      heartbeat: fresh(),
      minutes: { "/telerobot/minutes/2026-10": "8984" },
    });
    await reap(deps);
    expect(ec2.stop).not.toHaveBeenCalled();
  });
});

describe("checkOrigin", () => {
  it("accepts equal, rejects unequal, different length, or missing", () => {
    const d = { originSecret: SECRET };
    expect(checkOrigin(d, GOOD)).toBe(true);
    expect(checkOrigin(d, { "x-telerobot-origin": "s3cre!" })).toBe(false);
    expect(checkOrigin(d, { "x-telerobot-origin": "short" })).toBe(false);
    expect(checkOrigin(d, {})).toBe(false);
    expect(checkOrigin({ originSecret: "" }, { "x-telerobot-origin": "" })).toBe(false);
  });
});

describe("syncLabDns", () => {
  const V6 = "2a05:d01c:d9c:1900:1234:5678:9abc:def0";
  function withDns(state: string, ipv6?: string) {
    const made = makeDeps({ state });
    made.ec2.describeState.mockResolvedValue({ state, launchTime: NOW, ipv6 });
    const upsert = vi.fn(async (_ipv6: string) => {});
    made.deps.labDns = { upsert };
    return { ...made, upsert };
  }

  it("points the record at the lab's IPv6 address", async () => {
    const { deps, upsert, ec2 } = withDns("running", V6);
    expect(await syncLabDns(deps)).toBe(V6);
    expect(upsert).toHaveBeenCalledWith(V6);
    expect(ec2.describeState).toHaveBeenCalledWith("i-123");
  });

  it("keeps the same address whatever the lab's state: nothing to follow, nothing parked", async () => {
    for (const state of ["pending", "running", "stopping", "stopped"]) {
      const { deps, upsert } = withDns(state, V6);
      expect(await syncLabDns(deps), state).toBe(V6);
      expect(upsert).toHaveBeenCalledWith(V6);
    }
  });

  it("leaves the record alone when the instance has no IPv6 address", async () => {
    const { deps, upsert } = withDns("terminated", undefined);
    expect(await syncLabDns(deps)).toBeNull();
    expect(upsert).not.toHaveBeenCalled();
  });

  it("does nothing without a record", async () => {
    const { deps, ec2 } = makeDeps({ state: "running" });
    expect(await syncLabDns(deps)).toBeNull();
    expect(ec2.describeState).not.toHaveBeenCalled();
  });
});
