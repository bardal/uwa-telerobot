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

    it(`${page} has the favicon`, () => {
      const html = read(page);
      expect(html).toMatch(/<link rel="icon" href="\/favicon\.ico"/);
      expect(html).toMatch(/<link rel="apple-touch-icon" href="\/apple-touch-icon\.png"/);
    });
  }

  it('the icons exist: the arm from the original site\'s logo', () => {
    const ico = readFileSync(join(root, 'public/favicon.ico'));
    expect([...ico.subarray(0, 4)]).toEqual([0, 0, 1, 0]); // an ICO file
    expect(ico.readUInt16LE(4)).toBe(3); // 16, 32 and 48 px
    const png = readFileSync(join(root, 'public/apple-touch-icon.png'));
    expect(png.subarray(1, 4).toString()).toBe('PNG');
  });

  it('help.html uses the site look', () => {
    expect(read('public/help.html')).toMatch(/<body class="site-page">/);
  });

  it('the front page uses the site look', () => {
    expect(new LandingPage().el.classList.contains('site-page')).toBe(true);
  });
});
