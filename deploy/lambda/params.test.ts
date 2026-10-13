import { describe, expect, it, vi } from "vitest";
import { cachedValue, isReapEvent } from "./params";

describe("cachedValue", () => {
  it("prefers the environment value and never fetches", async () => {
    const fetch = vi.fn(async () => "from-ssm");
    expect(await cachedValue("from-env", "/p", fetch)()).toBe("from-env");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("fetches the parameter once and caches it", async () => {
    const fetch = vi.fn(async (n: string) => `value of ${n}`);
    const get = cachedValue(undefined, "/telerobot/turnstile-secret", fetch);
    expect(await get()).toBe("value of /telerobot/turnstile-secret");
    expect(await get()).toBe("value of /telerobot/turnstile-secret");
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("does not cache a failure", async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new Error("throttled")).mockResolvedValueOnce("ok");
    const get = cachedValue("", "/p", fetch);
    await expect(get()).rejects.toThrow("throttled");
    expect(await get()).toBe("ok");
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("is empty with neither", async () => {
    expect(await cachedValue(undefined, undefined, vi.fn())()).toBe("");
  });
});

describe("isReapEvent", () => {
  it("accepts the schedule's {reap:true} input and plain scheduled events", () => {
    expect(isReapEvent({ reap: true })).toBe(true);
    expect(isReapEvent({ source: "aws.events" })).toBe(true);
    expect(isReapEvent({ "detail-type": "Scheduled Event" })).toBe(true);
  });
  it("rejects function URL requests", () => {
    expect(isReapEvent({ rawPath: "/api/wake", body: '{"reap":true}' })).toBe(false);
    expect(isReapEvent({ reap: "true" })).toBe(false);
  });
});
