// Journey 7: the dev gateway sends the CSP CloudFront will. webui/csp.json is the single
// source: Vite copies it to dist/csp.json for the gateway, and deploy/lib/csp.ts reads it
// for CloudFront's response headers policy. An injected inline script is blocked.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import { CSP as CLOUDFRONT_CSP } from '../../deploy/lib/csp';
import { devLogin, open, shot } from './helpers';

const here = dirname(fileURLToPath(import.meta.url));
const SOURCE = (JSON.parse(readFileSync(join(here, '..', 'csp.json'), 'utf8')) as { csp: string }).csp;
const BUILT = (JSON.parse(readFileSync(join(here, '..', 'dist', 'csp.json'), 'utf8')) as { csp: string }).csp;

test('csp: the dev gateway sends the CloudFront policy, and an inline script is blocked', async ({ page, request }) => {
  expect(BUILT).toBe(SOURCE);
  expect(CLOUDFRONT_CSP).toBe(SOURCE);

  for (const path of ['/', '/index.html', '/config.json', '/help.html']) {
    const r = await request.get(path);
    expect(r.status(), path).toBe(200);
    expect(r.headers()['content-security-policy'], path).toBe(SOURCE);
    expect(r.headers()['x-content-type-options'], path).toBe('nosniff');
  }

  // the page itself runs under the policy (its own scripts load; the UI comes up)
  const main = await page.goto('/?e2e=1');
  expect(await main!.headerValue('content-security-policy')).toBe(SOURCE);
  await open(page);

  // an injected inline script raises a securitypolicyviolation and does not run
  const result = await page.evaluate(
    () =>
      new Promise<{ directive: string; disposition: string; ran: boolean }>((resolve) => {
        document.addEventListener('securitypolicyviolation', (e) => {
          setTimeout(() => resolve({
            directive: e.effectiveDirective,
            disposition: e.disposition,
            ran: (window as unknown as { __injected?: boolean }).__injected === true,
          }), 50);
        }, { once: true });
        const s = document.createElement('script');
        s.textContent = 'window.__injected = true;';
        document.head.appendChild(s);
      }),
  );
  expect(result).toEqual({ directive: 'script-src-elem', disposition: 'enforce', ran: false });

  // and with the policy in force the app still works end to end
  await devLogin(page, 'csp');
  await expect(page.locator('.control-page')).toBeVisible();
  await shot(page, 'csp-control-page');
});
