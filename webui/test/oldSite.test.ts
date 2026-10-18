// The pages preserved from the original site (public/old, built by
// scripts/preserve-old-site.mjs): safe to serve, and every link and image works.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import oldSite from '../src/content/old-site.json';

const PUBLIC = join(__dirname, '..', 'public');
const OLD = join(PUBLIC, 'old');

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    return statSync(p).isDirectory() ? files(p) : [p];
  });
}
const all = files(OLD);
const pages = all.filter((f) => f.endsWith('.html'));
const read = (f: string) => readFileSync(f, 'utf8');

/** The local file a URL in a preserved page points at, or null for an external one. */
function target(page: string, url: string): string | null {
  if (/^(https?:)?\/\//i.test(url) || url.startsWith('#') || url.startsWith('data:')) return null;
  const path = decodeURIComponent(url.split(/[?#]/)[0]!);
  if (!path) return null;
  return path.startsWith('/') ? join(PUBLIC, path) : resolve(dirname(page), path);
}

describe('preserved pages of the original site', () => {
  it('exist, and the landing page’s list points at them', () => {
    expect(pages.length).toBeGreaterThan(10);
    for (const e of oldSite) {
      expect(existsSync(join(PUBLIC, e.href)), e.href).toBe(true);
      expect(e.what.split(/\s+/).length, e.title).toBeLessThanOrEqual(25);
    }
  });

  it('carry no scripts, embeds, refreshes or event handlers', () => {
    for (const f of pages) {
      const s = read(f).toLowerCase();
      const at = relative(OLD, f);
      expect(s, at).not.toMatch(/<script|<applet|<object|<embed|<iframe|http-equiv=["']?refresh|<base\b/);
      expect(s, at).not.toMatch(/\son[a-z]+\s*=/);
      expect(s, at).toContain('<meta charset="utf-8">');
      expect(s, at).toContain('class="old-banner"');
    }
  });

  it('hold no email addresses, IP addresses or submitting forms', () => {
    for (const f of pages) {
      const s = read(f);
      const at = relative(OLD, f);
      expect(s, at).not.toMatch(/mailto:/i);
      expect(s, at).not.toMatch(/[\w.+-]+@[\w-]+\.[\w.]{2,}/);
      expect(s, at).not.toMatch(/\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/);
      expect(s, at).not.toMatch(/<form[^>]*\saction=/i);
    }
  });

  it('load every image locally, and every local link and image exists', () => {
    for (const f of pages) {
      const s = read(f);
      const at = relative(OLD, f);
      for (const m of s.matchAll(/<img[^>]*\ssrc=["']?([^"'\s>]+)/gi)) {
        const t = target(f, m[1]!);
        expect(t, `${at}: external image ${m[1]}`).not.toBeNull();
        expect(existsSync(t!), `${at}: missing ${m[1]}`).toBe(true);
      }
      for (const m of s.matchAll(/<(?:a|link)[^>]*\shref=["']?([^"'\s>]+)/gi)) {
        const url = m[1]!;
        if (url === '/' || url.startsWith('/#')) continue; // the app itself
        const t = target(f, url);
        if (t) expect(existsSync(t), `${at}: broken link ${url}`).toBe(true);
      }
    }
  });

  it('opens external links in a new tab', () => {
    for (const f of pages) {
      for (const m of read(f).matchAll(/<a\b[^>]*href=["']?https?:[^>]*>/gi)) {
        expect(m[0], relative(OLD, f)).toMatch(/rel=["']?noopener/i);
      }
    }
  });
});
