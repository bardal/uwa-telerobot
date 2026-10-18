// @vitest-environment happy-dom
import { existsSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { APPLET_HASH, FORMS_HASH, GALLERY, HISTORY, LandingPage, STYLING_NOTE, longDate } from '../src/ui/landingPage';
import { DALTON_THESIS } from '../src/ui/wakingPage';

const PUBLIC = join(__dirname, '..', 'public');
const images = [...GALLERY.hall, ...GALLERY.towers, ...GALLERY.creations].flatMap((e) => e.images);

describe('landing page', () => {
  it('shows the ways in, the history and the saved builds', () => {
    const p = new LandingPage();
    const text = p.el.textContent!;
    expect([...p.el.querySelectorAll('.landing-go')].map((a) => a.getAttribute('href'))).toEqual([APPLET_HASH, FORMS_HASH]);
    for (const e of HISTORY) expect(text).toContain(e.text);
    expect(p.el.querySelectorAll('.landing-build').length).toBe(GALLERY.hall.length + GALLERY.creations.length);
    expect(p.el.querySelectorAll('.landing-towers tr').length).toBe(GALLERY.towers.length + 1);
    expect(text).toContain('785 mm');
    expect(p.el.querySelector('.landing-styling')!.textContent).toBe(STYLING_NOTE);
    expect(STYLING_NOTE).toMatch(/^2000s styling preserved/);
    for (const a of p.el.querySelectorAll<HTMLAnchorElement>('a[target=_blank]')) expect(a.rel).toBe('noopener');
  });

  it('links the thesis PDF this site serves', () => {
    const f = join(PUBLIC, DALTON_THESIS);
    expect(readFileSync(f).subarray(0, 5).toString()).toBe('%PDF-');
    const links = [...new LandingPage().el.querySelectorAll<HTMLAnchorElement>('a')].filter((a) => a.getAttribute('href') === DALTON_THESIS);
    expect(links.length).toBe(1);
  });

  it('every photo has alt text, exists in public/ and loads lazily below the top', () => {
    expect(images.length).toBeGreaterThan(10);
    let bytes = 0;
    for (const i of images) {
      expect(i.alt.trim().length, i.src).toBeGreaterThan(5);
      expect(i.src).toMatch(/^\/gallery\/(hall|towers|creations)\/[a-z0-9.-]+\.(jpg|gif)$/);
      const f = join(PUBLIC, i.src);
      expect(existsSync(f), i.src).toBe(true);
      bytes += statSync(f).size;
    }
    expect(bytes).toBeLessThan(3_000_000);
    const lazy = [...new LandingPage().el.querySelectorAll('.landing-build img')];
    expect(lazy.every((img) => img.getAttribute('loading') === 'lazy')).toBe(true);
  });

  it('names builders by nickname only: no hostnames, IP addresses or emails', () => {
    const all = JSON.stringify(GALLERY);
    expect(all).not.toMatch(/\b\d{1,3}(\.\d{1,3}){3}\b/);
    expect(all).not.toMatch(/[\w.+-]+@[\w-]+\.[\w.]+/);
    expect(all).not.toMatch(/\b[\w-]+\.(com|net|org|edu|au|nl|ch|cz|nz|uk)\b/i);
  });

  it('writes ISO dates out in full and leaves others as they are', () => {
    expect(longDate('1999-11-25')).toBe('25 November 1999');
    expect(longDate('July 1999')).toBe('July 1999');
    expect(longDate(null)).toBe('');
  });
});

describe('landing page: the original site', () => {
  it('lists the preserved pages by group, each linking under /old/', () => {
    const p = new LandingPage();
    const links = [...p.el.querySelectorAll<HTMLAnchorElement>('.landing-old a')].map((a) => a.getAttribute('href'));
    expect(links.length).toBeGreaterThan(8);
    expect(links.every((h) => h!.startsWith('/old/'))).toBe(true);
    expect(p.el.textContent).toContain('The 1997 interface competition');
  });
});
