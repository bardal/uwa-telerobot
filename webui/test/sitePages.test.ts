// @vitest-environment happy-dom
// The site's own pages share public/site.css. The CSP is style-src 'self', so an inline
// <style> or style="" is silently dropped in production: help.html once rendered unstyled.

import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { LandingPage } from '../src/ui/landingPage';

const root = join(__dirname, '..');
const read = (p: string) => readFileSync(join(root, p), 'utf8');

describe('site pages', () => {
  for (const page of ['index.html', 'public/help.html']) {
    it(`${page} links site.css and has no inline styles`, () => {
      const html = read(page);
      expect(html).toMatch(/<link rel="stylesheet" href="\/site\.css"/);
      expect(html).not.toMatch(/<style[\s>]/i);
      expect(html).not.toMatch(/\sstyle="/i);
    });
  }

  it('help.html uses the site look', () => {
    expect(read('public/help.html')).toMatch(/<body class="site-page">/);
  });

  it('the front page uses the site look', () => {
    expect(new LandingPage().el.classList.contains('site-page')).toBe(true);
  });
});
