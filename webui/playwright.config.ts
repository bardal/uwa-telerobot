import { defineConfig, devices } from '@playwright/test';

// The journeys run against the dev stack (e2e/stack.mjs): the gateway jar with the lab
// in process, serving dist/. Build first: `npm run build && npx playwright test`.
const port = Number(process.env.E2E_PORT ?? 8080);
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: 'e2e',
  outputDir: 'test-results',
  // one shared lab: journeys that take control must not overlap
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  use: {
    baseURL,
    ...devices['Desktop Chrome'],
    viewport: { width: 1280, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'node e2e/stack.mjs',
    url: `${baseURL}/healthz`,
    reuseExistingServer: false,
    timeout: 180_000,
    stdout: 'pipe',
    stderr: 'pipe',
    env: { E2E_PORT: String(port) },
  },
});
