// End-to-end smoke test of a deployed telerobot.
//
//   node deploy/smoke.mjs https://<distribution domain>
//
// Environment (deploy.ps1 sets all of it; nothing here is ever committed):
//   SMOKE_USER_POOL_ID   the Cognito user pool id (stack output UserPoolId)
//   SMOKE_CLIENT_ID      the smoke app client id (stack output SmokeClientId)
//   SMOKE_PASSWORD       smoke@telerobot.invalid's password, fresh each run
//   SMOKE_USERNAME       optional, default smoke@telerobot.invalid
//   SMOKE_COMMIT         optional: the commit /healthz must report
//   SMOKE_WAKE_TIMEOUT_S optional: how long to wait for the lab, default 900
//
// The WAF common rule set blocks requests without a User-Agent, so every
// request carries one. Exits non-zero on the first failed check.

import {
  AdminInitiateAuthCommand,
  CognitoIdentityProviderClient,
} from "@aws-sdk/client-cognito-identity-provider";
import { readFileSync } from "node:fs";
import https from "node:https";
import WebSocket from "ws";

const USER_AGENT = "telerobot-smoke/1.0 (+deploy/smoke.mjs)";
const REGION = "eu-west-2";
// A move from the gateway's BridgeIT pick-and-place: a known-reachable pose.
const MOVE = "x330,y120,z90";

const base = (process.argv[2] ?? "").replace(/\/+$/, "");
if (!/^https:\/\/[a-z0-9.-]+$/i.test(base)) {
  console.error("usage: node deploy/smoke.mjs https://<distribution domain>");
  process.exit(2);
}
const host = new URL(base).host;
const env = (name, fallback) => {
  const v = process.env[name] ?? fallback;
  if (v === undefined || v === "") {
    console.error(`missing environment variable ${name}`);
    process.exit(2);
  }
  return v;
};
const poolId = env("SMOKE_USER_POOL_ID");
const clientId = env("SMOKE_CLIENT_ID");
const password = env("SMOKE_PASSWORD");
const username = env("SMOKE_USERNAME", "smoke@telerobot.invalid");
const expectCommit = process.env.SMOKE_COMMIT || null;
const wakeTimeoutMs = Number(process.env.SMOKE_WAKE_TIMEOUT_S ?? 900) * 1000;

const results = [];
function pass(name, detail = "") {
  results.push(name);
  console.log(`PASS ${name}${detail ? `: ${detail}` : ""}`);
}
function fail(name, detail) {
  console.log(`FAIL ${name}: ${detail}`);
  console.log(`\n${results.length} check(s) passed before the failure.`);
  process.exit(1);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// A network error anywhere (DNS, TLS, a timeout) fails the run with its cause.
const crash = (e) => fail("network", `${e?.name}: ${e?.message}${e?.cause ? ` (${e.cause.message ?? e.cause})` : ""}`);
process.on("uncaughtException", crash);
process.on("unhandledRejection", crash);

// Every HTTP request carries the site's own Origin and a User-Agent, as a browser would.
const COMMON_HEADERS = { "user-agent": USER_AGENT, origin: base };
async function get(path, init = {}) {
  return fetch(base + path, {
    ...init,
    redirect: "manual",
    signal: AbortSignal.timeout(30_000),
    headers: { ...COMMON_HEADERS, ...(init.headers ?? {}) },
  });
}
// A GET whose path goes on the wire exactly as given: fetch() (WHATWG URL)
// resolves "..", "%2e%2e" and friends before sending, which would hide a
// traversal hole. Resolves {status, body}.
function rawGet(path) {
  return new Promise((resolve, reject) => {
    const req = https.request(
      { host, port: 443, method: "GET", path, headers: { ...COMMON_HEADERS }, timeout: 30_000 },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => resolve({ status: res.statusCode, body: Buffer.concat(chunks).toString("utf8") }));
        res.on("error", reject);
      },
    );
    req.on("timeout", () => req.destroy(new Error(`timeout on ${path}`)));
    req.on("error", reject);
    req.end();
  });
}
async function readJson(res) {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { _raw: text.slice(0, 200) };
  }
}

// ---- 0. the UI's security headers (managed policy + SiteHeaders function) ----
{
  const csp = JSON.parse(readFileSync(new URL("../webui/csp.json", import.meta.url), "utf8")).csp;
  const res = await get("/");
  await res.arrayBuffer();
  const want = {
    "content-security-policy": csp,
    "permissions-policy": "camera=(), microphone=(), geolocation=()",
    "cross-origin-opener-policy": "same-origin",
    "x-content-type-options": "nosniff",
  };
  const wrong = Object.entries(want).filter(([k, v]) => res.headers.get(k) !== v).map(([k]) => k);
  if (!res.headers.get("strict-transport-security")) wrong.push("strict-transport-security");
  if (res.status !== 200 || wrong.length > 0) fail("headers", `HTTP ${res.status}, wrong or missing: ${wrong.join(", ")}`);
  pass("headers");
}

// ---- 1. GET /api/status ----
{
  const res = await get("/api/status");
  const body = await readJson(res);
  if (res.status !== 200 || typeof body.state !== "string") {
    fail("status", `HTTP ${res.status} ${JSON.stringify(body)}`);
  }
  pass("status", JSON.stringify(body));
}

// ---- 2. sign in and POST /api/wake ----
let idToken;
{
  const cognito = new CognitoIdentityProviderClient({ region: REGION });
  try {
    const out = await cognito.send(
      new AdminInitiateAuthCommand({
        UserPoolId: poolId,
        ClientId: clientId,
        AuthFlow: "ADMIN_USER_PASSWORD_AUTH",
        AuthParameters: { USERNAME: username, PASSWORD: password },
      }),
    );
    idToken = out.AuthenticationResult?.IdToken;
    if (!idToken) fail("sign-in", `no IdToken (challenge ${out.ChallengeName ?? "none"})`);
  } catch (e) {
    fail("sign-in", `${e.name}: ${e.message}`);
  }
  pass("sign-in", username);

  const res = await get("/api/wake", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ idToken }),
  });
  const body = await readJson(res);
  if (res.status !== 200 && res.status !== 202) fail("wake", `HTTP ${res.status} ${JSON.stringify(body)}`);
  pass("wake", `HTTP ${res.status} ${JSON.stringify(body)}`);
}

// ---- 3. poll /healthz until the lab is ready ----
{
  const started = Date.now();
  const deadline = started + wakeTimeoutMs;
  const secs = () => Math.round((Date.now() - started) / 1000);
  let last = "";
  let woke = false;
  // When the gateway first answered through CloudFront (the lab's DNS record
  // must have followed the instance by then), as against when it was ready.
  let firstAnswer = null;
  for (;;) {
    try {
      const res = await get("/healthz");
      const body = await readJson(res);
      last = `HTTP ${res.status} ${JSON.stringify(body)}`;
      if (firstAnswer === null && res.status === 200 && body.ok === true) firstAnswer = secs();
      // Ready means lab === "ready" (MOM, the robot proxy and the camera
      // server all up; "starting" until then), on the expected commit. A
      // ready gateway on another commit is the previous boot still serving
      // while deploy.ps1's reboot lands, so keep polling; the deadline
      // reports it.
      if (res.status === 200 && body.ok === true && body.lab === "ready") {
        if (!expectCommit || body.commit === expectCommit) {
          pass("healthz", `${JSON.stringify(body)}; gateway first answered after ${firstAnswer} s, ready after ${secs()} s`);
          break;
        }
        last += ` (commit ${body.commit}, expected ${expectCommit})`;
      }
    } catch (e) {
      last = `${e.name}: ${e.message}`;
    }
    if (Date.now() > deadline) fail("healthz", `not ready after ${wakeTimeoutMs / 1000} s; last ${last}`);
    // The reaper or a stop between deploys can leave it stopped: wake again.
    if (!woke && Date.now() > deadline - wakeTimeoutMs / 2) {
      woke = true;
      await get("/api/wake", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ idToken }),
      }).catch(() => {});
    }
    await sleep(10_000);
  }
}

// ---- 4. WebSocket session: welcome, image, control, a move ----
function openSocket(origin) {
  return new WebSocket(`wss://${host}/ws`, {
    origin,
    headers: { "user-agent": USER_AGENT },
    handshakeTimeout: 20_000,
  });
}

// The archived move: {id, day}, from the gateway's `archived` message.
const archivedMove = await new Promise((resolve) => {
  const ws = openSocket(base);
  const step = { welcome: null, image: false, jpeg: false, control: false, done: false, archived: false };
  let awaitingJpeg = false;
  const timer = setTimeout(() => {
    ws.terminate();
    fail("websocket", `timed out; progress ${JSON.stringify({ ...step, welcome: !!step.welcome })}`);
  }, 240_000);
  const send = (m) => ws.send(JSON.stringify(m));

  ws.on("unexpected-response", (_req, res) => fail("websocket", `handshake HTTP ${res.statusCode}`));
  ws.on("error", (e) => fail("websocket", `${e.name}: ${e.message}`));
  ws.on("close", (code, reason) => {
    if (!step.archived) fail("websocket", `closed early: ${code} ${reason}`);
  });
  ws.on("open", () => send({ t: "hello", nick: "smoke", idToken }));

  ws.on("message", (data, isBinary) => {
    if (isBinary) {
      if (awaitingJpeg) {
        awaitingJpeg = false;
        const buf = Buffer.from(data);
        if (buf[0] !== 0xff || buf[1] !== 0xd8) fail("image", "the binary frame is not a JPEG");
        if (!step.jpeg) {
          step.jpeg = true;
          pass("image", `${buf.length} bytes`);
          send({ t: "takeControl" });
        }
      }
      return;
    }
    let m;
    try {
      m = JSON.parse(data.toString());
    } catch {
      fail("websocket", "a text frame is not JSON");
    }
    switch (m.t) {
      case "welcome":
        step.welcome = m;
        if (expectCommit && m.commit !== expectCommit) fail("welcome", `commit ${m.commit}`);
        pass("welcome", `nick ${m.nick}, level ${m.level}, cameras ${m.cameras.join(",")}`);
        if (m.cameras.length === 0) fail("welcome", "no cameras");
        send({ t: "selectCamera", camera: m.cameras[0] });
        // selectCamera replays the last photo only if there is one: ask for a fresh one too.
        send({ t: "newImage", camera: m.cameras[0] });
        break;
      case "image":
        if (!Array.isArray(m.calibration) || m.calibration.length !== 12) fail("image", "calibration is not 12 numbers");
        step.image = true;
        awaitingJpeg = true;
        break;
      case "controller":
        if (step.jpeg && !step.control && step.welcome && m.nick === step.welcome.nick) {
          step.control = true;
          pass("takeControl");
          send({ t: "command", script: MOVE });
        }
        break;
      case "commandStatus":
        if (!step.control) break;
        if (m.state === "error") fail("command", `${m.script}: ${m.text}`);
        if (m.state === "done" && !step.done) {
          step.done = true;
          pass("command", `${m.script} done`);
          send({ t: "release" });
          // stay connected until the gateway has archived the move (after-photos)
        }
        break;
      case "archived": {
        if (!step.control || m.kind !== "move") break;
        const day = /\/archive\/public\/index\/(\d{4}-\d{2}-\d{2})\.json$/.exec(m.url ?? "")?.[1];
        if (!m.id || !day) fail("archived", `unexpected ${JSON.stringify(m)}`);
        step.archived = true;
        pass("archived", `move ${m.id} on ${day}`);
        clearTimeout(timer);
        ws.close(1000, "smoke done");
        resolve({ id: m.id, day });
        break;
      }
      case "error":
        fail("websocket", `gateway error ${m.code}: ${m.message}`);
        break;
      default:
        break;
    }
  });
});

// ---- 5. the public archive index lists the move ----
{
  // The gateway flushes its indexes (and the audit log) every 60 s, and the
  // indexes are cached for 30 s (CloudFront caches a miss for 10 s).
  const { id, day } = archivedMove;
  let last = "";
  let ok = false;
  for (let i = 0; i < 18 && !ok; i++) {
    if (i > 0) await sleep(10_000);
    const daysRes = await get(`/archive/public/index/days.json`);
    const days = await readJson(daysRes);
    last = `days.json HTTP ${daysRes.status} ${JSON.stringify(days).slice(0, 120)}`;
    if (daysRes.status !== 200 || !Array.isArray(days.days) || !days.days.includes(day)) continue;
    const idxRes = await get(`/archive/public/index/${day}.json`);
    const idxText = await idxRes.text();
    last = `${day}.json HTTP ${idxRes.status}, ${idxText.length} bytes`;
    ok = idxRes.status === 200 && idxText.includes(`"${id}"`);
  }
  if (!ok) fail("archive public", `move ${id} not listed after 3 minutes; last ${last}`);
  pass("archive public", `days.json has ${day}; ${day}.json has move ${id}`);
}

// ---- 6. the private archive is never served ----
{
  // private/audit/<day>.jsonl really exists now (written in the same flush
  // as the index above), so a 403/404 here is the refusal, not a miss.
  const { id, day } = archivedMove;
  const key = `private/audit/${day}.jsonl`;
  // [path, accepted statuses]: the two plain probes must be 403/404; the
  // encoded variants may also be rejected as malformed (any 4xx).
  const fourXX = (s) => s >= 400 && s < 500;
  const probes = [
    [`/archive/${key}`, (s) => s === 403 || s === 404],
    [`/archive/public/../${key}`, (s) => s === 403 || s === 404],
    [`/archive/public/%2e%2e/${key}`, fourXX],
    [`/archive/public/..%2f${key}`, fourXX],
  ];
  for (const [path, accepted] of probes) {
    const { status, body } = await rawGet(path);
    const leaked = [`"${id}"`, '"ipHash"', '"what"', '"sub"'].filter((s) => body.includes(s));
    if (!accepted(status)) fail("archive private", `${path}: HTTP ${status}`);
    if (leaked.length > 0) fail("archive private", `${path}: HTTP ${status} but the body holds audit content (${leaked.join(", ")})`);
    pass("archive private", `${path}: HTTP ${status}, no audit content`);
  }
}

// ---- 7. a bad Origin is refused at the WebSocket handshake with 403 ----
await new Promise((resolve) => {
  const ws = openSocket("https://evil.example");
  const stop = () => {
    ws.removeAllListeners();
    ws.on("error", () => {});
    ws.terminate();
  };
  ws.on("unexpected-response", (_req, res) => {
    stop();
    if (res.statusCode !== 403) fail("bad origin refused", `handshake HTTP ${res.statusCode}, expected 403`);
    pass("bad origin refused", "handshake HTTP 403");
    resolve();
  });
  ws.on("error", (e) => {
    stop();
    fail("bad origin refused", `expected an HTTP 403, got ${e.name}: ${e.message}`);
  });
  ws.on("open", () => {
    stop();
    fail("bad origin refused", "the handshake succeeded");
  });
});

console.log(`\nAll ${results.length} smoke checks passed against ${base}.`);
process.exit(0);
