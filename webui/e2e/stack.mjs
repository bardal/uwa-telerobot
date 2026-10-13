// The dev stack for the Playwright journeys: the gateway jar in dev mode with the
// 2000 lab in process, serving the built UI (dist/) and a fresh temporary archive.
// It builds nothing: run `npm run build` (and `mvn package` for the gateway) first.
//
//   node e2e/stack.mjs            (E2E_PORT overrides 8080)

import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, readdirSync, rmSync, statSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const webui = resolve(here, '..');
const jar = resolve(webui, '..', 'gateway', 'target', 'gateway.jar');
const dist = resolve(webui, 'dist');
const port = Number(process.env.E2E_PORT ?? 8080);

function fail(message) {
  console.error(`e2e stack: ${message}`);
  process.exit(1);
}

if (!existsSync(jar)) {
  fail(`${jar} is missing. Build it first: in the repo root run \`mvn -q -o package\` (JDK 21) or \`mvn -q -o -pl gateway -am package\`.`);
}
if (!existsSync(join(dist, 'index.html')) || !existsSync(join(dist, 'csp.json'))) {
  fail(`${dist} has no built UI. Run \`npm run build\` in webui/ first (\`npm run e2e\` expects it).`);
}

function javaExe() {
  const exe = process.platform === 'win32' ? 'java.exe' : 'java';
  const homes = [process.env.JAVA_HOME, join(homedir(), 'tools', 'jdk21')].filter(Boolean);
  for (const h of homes) {
    const p = join(h, 'bin', exe);
    if (existsSync(p)) return p;
  }
  return 'java'; // on the PATH, then
}

// On Windows Playwright ends the stack with `taskkill /T /F`, so the exit handler below never
// runs: clear work directories that earlier runs left behind (an hour old, so never a live run's).
for (const name of readdirSync(tmpdir())) {
  if (!name.startsWith('telerobot-e2e-')) continue;
  const p = join(tmpdir(), name);
  try {
    if (Date.now() - statSync(p).mtimeMs > 3_600_000) rmSync(p, { recursive: true, force: true });
  } catch { /* in use or gone */ }
}

// a fresh work directory per run: the archive, and the logs the 2000 lab writes to its working directory
const work = mkdtempSync(join(tmpdir(), 'telerobot-e2e-'));
const archiveDir = join(work, 'archive');
const args = [
  '-jar', jar,
  '--dev', '--lab-in-process',
  '--port', String(port),
  '--static-dir', dist,
  '--archive-dir', archiveDir,
];
console.log(`e2e stack: ${javaExe()} ${args.join(' ')}`);
const child = spawn(javaExe(), args, { cwd: work, stdio: ['ignore', 'inherit', 'inherit'] });

let stopping = false;
function cleanup() {
  try { rmSync(work, { recursive: true, force: true }); } catch { /* still in use */ }
}
function stop() {
  if (stopping) return;
  stopping = true;
  if (child.exitCode === null) child.kill();
}
for (const sig of ['SIGINT', 'SIGTERM', 'SIGHUP']) process.on(sig, () => { stop(); });
process.on('exit', () => { if (child.exitCode === null) child.kill(); cleanup(); });
child.on('exit', (code) => {
  if (!stopping) console.error(`e2e stack: the gateway exited with ${code}`);
  cleanup();
  process.exit(stopping ? 0 : (code ?? 1));
});

// Playwright waits for /healthz itself; this only reports when the stack is up, or gives up.
const deadline = Date.now() + 120_000;
(async () => {
  while (Date.now() < deadline && !stopping && child.exitCode === null) {
    try {
      const r = await fetch(`http://127.0.0.1:${port}/healthz`);
      if (r.ok) {
        console.log(`e2e stack: ready on http://127.0.0.1:${port} (archive ${archiveDir})`);
        return;
      }
    } catch { /* not yet */ }
    await new Promise((r) => setTimeout(r, 500));
  }
  if (!stopping && child.exitCode === null) {
    console.error('e2e stack: /healthz did not answer within 120 s');
    stop();
  }
})();
