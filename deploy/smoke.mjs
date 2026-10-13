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

async function get(path, init = {}) {
  return fetch(base + path, {
    ...init,
    redirect: "manual",
    signal: AbortSignal.timeout(30_000),
    headers: { "user-agent": USER_AGENT, ...(init.headers ?? {}) },
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
  const deadline = Date.now() + wakeTimeoutMs;
  let last = "";
  let woke = false;
  for (;;) {
    try {
      const res = await get("/healthz");
      const body = await readJson(res);
      last = `HTTP ${res.status} ${JSON.stringify(body)}`;
      if (res.status === 200 && body.ok === true && body.lab === "ready") {
        if (expectCommit && body.commit !== expectCommit) {
          fail("healthz", `commit ${body.commit}, expected ${expectCommit}`);
        }
        pass("healthz", JSON.stringify(body));
        break;
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

await new Promise((resolve) => {
  const ws = openSocket(base);
  const step = { welcome: null, image: false, jpeg: false, control: false, done: false };
  let awaitingJpeg = false;
  const timer = setTimeout(() => {
    ws.terminate();
    fail("websocket", `timed out; progress ${JSON.stringify({ ...step, welcome: !!step.welcome })}`);
  }, 120_000);
  const send = (m) => ws.send(JSON.stringify(m));

  ws.on("unexpected-response", (_req, res) => fail("websocket", `handshake HTTP ${res.statusCode}`));
  ws.on("error", (e) => fail("websocket", `${e.name}: ${e.message}`));
  ws.on("close", (code, reason) => {
    if (!step.done) fail("websocket", `closed early: ${code} ${reason}`);
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
        if (m.cameras.length > 0) send({ t: "selectCamera", camera: m.cameras[0] });
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
        if (m.state === "done") {
          step.done = true;
          pass("command", `${m.script} done`);
          send({ t: "release" });
          clearTimeout(timer);
          ws.close(1000, "smoke done");
          resolve();
        }
        break;
      case "error":
        fail("websocket", `gateway error ${m.code}: ${m.message}`);
        break;
      default:
        break;
    }
  });
});

// ---- 5. the public archive index ----
{
  // The gateway writes days.json when it archives a move; allow it a moment.
  let res;
  for (let i = 0; i < 6; i++) {
    res = await get("/archive/public/index/days.json");
    if (res.status === 200) break;
    await sleep(10_000);
  }
  if (res.status !== 200) fail("archive public", `HTTP ${res.status}`);
  const body = await readJson(res);
  pass("archive public", `days.json ${JSON.stringify(body).slice(0, 80)}`);
}

// ---- 6. the private archive is never served ----
{
  const res = await get("/archive/private/audit/x");
  if (res.status !== 403 && res.status !== 404) fail("archive private", `HTTP ${res.status}`);
  pass("archive private", `HTTP ${res.status}`);
}

// ---- 7. a bad Origin is refused at the WebSocket handshake ----
await new Promise((resolve) => {
  const ws = openSocket("https://evil.example");
  const done = (detail) => {
    ws.removeAllListeners();
    ws.on("error", () => {});
    ws.terminate();
    pass("bad origin refused", detail);
    resolve();
  };
  ws.on("unexpected-response", (_req, res) => done(`handshake HTTP ${res.statusCode}`));
  ws.on("error", (e) => done(`${e.name}: ${e.message}`));
  ws.on("open", () => {
    ws.terminate();
    fail("bad origin refused", "the handshake succeeded");
  });
});

console.log(`\nAll ${results.length} smoke checks passed against ${base}.`);
process.exit(0);
