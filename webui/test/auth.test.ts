import { describe, expect, it, vi } from 'vitest';
import { challengeFor, createAuth, generateVerifier, type AuthDeps } from '../src/net/auth';
import { TurnstileWidget, type TurnstileApi } from '../src/net/turnstile';
import { parseConfig } from '../src/config';

const cfg = {
  region: 'eu-west-2', userPoolDomain: 'pool.auth.eu-west-2.amazoncognito.com', clientId: 'cid',
  turnstileSiteKey: 'k', redirectUri: 'https://site.example/',
};

class MemStorage {
  m = new Map<string, string>();
  getItem(k: string) { return this.m.get(k) ?? null; }
  setItem(k: string, v: string) { this.m.set(k, v); }
  removeItem(k: string) { this.m.delete(k); }
}

function setup(over: Partial<AuthDeps> = {}, config = cfg) {
  const storage = new MemStorage();
  const nav: string[] = [];
  const replaced: string[] = [];
  let href = 'https://site.example/';
  const deps: AuthDeps = {
    config: () => Promise.resolve(config),
    fetchImpl: vi.fn() as unknown as typeof fetch,
    storage: () => storage as unknown as Storage,
    location: () => ({ href }),
    navigate: (u) => nav.push(u),
    replaceUrl: (u) => replaced.push(u),
    now: () => 1_000_000,
    ...over,
  };
  return { auth: createAuth(deps), storage, nav, replaced, setHref: (h: string) => { href = h; }, deps };
}

const b64u = (o: unknown) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
const jwt = (exp: number) => `${b64u({ alg: 'none' })}.${b64u({ exp })}.sig`;

describe('PKCE', () => {
  it('verifier is 43-128 URL-safe characters and random', () => {
    const v = generateVerifier();
    expect(v.length).toBeGreaterThanOrEqual(43);
    expect(v.length).toBeLessThanOrEqual(128);
    expect(v).toMatch(/^[A-Za-z0-9_-]+$/);
    expect(generateVerifier()).not.toBe(v);
  });

  it('challenge is base64url SHA-256 (RFC 7636 test vector)', async () => {
    expect(await challengeFor('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'))
      .toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });
});

describe('auth', () => {
  it('signIn redirects to the Hosted UI with S256 and stores state and verifier', async () => {
    const { auth, nav, storage } = setup();
    await auth.signIn();
    const u = new URL(nav[0]!);
    expect(u.origin + u.pathname).toBe('https://pool.auth.eu-west-2.amazoncognito.com/oauth2/authorize');
    expect(u.searchParams.get('response_type')).toBe('code');
    expect(u.searchParams.get('code_challenge_method')).toBe('S256');
    expect(u.searchParams.get('client_id')).toBe('cid');
    expect(u.searchParams.get('redirect_uri')).toBe('https://site.example/');
    // exactly what the Cognito web client allows (openid and email); anything more is refused
    expect(u.searchParams.get('scope')).toBe('openid email');
    const verifier = storage.getItem('telerobot.auth.verifier')!;
    expect(u.searchParams.get('code_challenge')).toBe(await challengeFor(verifier));
    expect(u.searchParams.get('state')).toBe(storage.getItem('telerobot.auth.state'));
  });

  it('signIn throws a clear error without a userPoolDomain', async () => {
    const { auth, nav } = setup({}, { ...cfg, userPoolDomain: '', clientId: '' });
    await expect(auth.signIn()).rejects.toThrow(/not configured/);
    expect(nav).toHaveLength(0);
  });

  it('handleRedirect exchanges the code and keeps the token', async () => {
    const id = jwt(2_000_000);
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify({ id_token: id, access_token: 'a' }))) as unknown as typeof fetch;
    const { auth, nav, storage, replaced, setHref } = setup({ fetchImpl });
    await auth.signIn();
    const state = new URL(nav[0]!).searchParams.get('state')!;
    const verifier = storage.getItem('telerobot.auth.verifier')!;
    setHref(`https://site.example/play.html?code=C1&state=${state}&x=1`);
    expect(await auth.handleRedirect()).toBe(true);
    expect(auth.idToken()).toBe(id);
    const [url, init] = (fetchImpl as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(url).toBe('https://pool.auth.eu-west-2.amazoncognito.com/oauth2/token');
    const body = new URLSearchParams(init.body as string);
    expect(body.get('grant_type')).toBe('authorization_code');
    expect(body.get('code')).toBe('C1');
    expect(body.get('code_verifier')).toBe(verifier);
    expect(replaced).toEqual(['/play.html?x=1']);
    expect(storage.getItem('telerobot.auth.verifier')).toBeNull();
  });

  it('rejects a mismatched state without calling the token endpoint', async () => {
    const fetchImpl = vi.fn() as unknown as typeof fetch;
    const { auth, setHref } = setup({ fetchImpl });
    await auth.signIn();
    setHref('https://site.example/?code=C1&state=evil');
    expect(await auth.handleRedirect()).toBe(false);
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(auth.idToken()).toBeNull();
  });

  it('an error redirect throws the reason and strips the params', async () => {
    const { auth, setHref, replaced } = setup();
    setHref('https://site.example/p?error=access_denied&error_description=Nope&state=s');
    await expect(auth.handleRedirect()).rejects.toThrow(/Nope/);
    expect(replaced).toEqual(['/p']);
  });

  it('a token with no parseable exp is treated as expired', () => {
    const { auth, storage } = setup();
    storage.setItem('telerobot.auth.tokens', JSON.stringify({ idToken: 'opaque' }));
    expect(auth.idToken()).toBeNull();
  });

  it('returns false when the URL has no code', async () => {
    expect(await setup().auth.handleRedirect()).toBe(false);
  });

  it('idToken survives a reload via sessionStorage and expires', async () => {
    const id = jwt(2_000_000);
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify({ id_token: id }))) as unknown as typeof fetch;
    const a = setup({ fetchImpl });
    await a.auth.signIn();
    a.setHref(`https://site.example/?code=C&state=${new URL(a.nav[0]!).searchParams.get('state')}`);
    await a.auth.handleRedirect();
    const reloaded = createAuth({ ...a.deps });
    expect(reloaded.idToken()).toBe(id);
    const expired = createAuth({ ...a.deps, now: () => 3_000_000_000 });
    expect(expired.idToken()).toBeNull();
  });

  it('signOut clears tokens and goes to /logout', async () => {
    const { auth, nav, storage } = setup();
    storage.setItem('telerobot.auth.tokens', JSON.stringify({ idToken: jwt(2_000_000) }));
    expect(auth.idToken()).not.toBeNull();
    await auth.signOut();
    expect(auth.idToken()).toBeNull();
    expect(storage.getItem('telerobot.auth.tokens')).toBeNull();
    const u = new URL(nav[0]!);
    expect(u.pathname).toBe('/logout');
    expect(u.searchParams.get('client_id')).toBe('cid');
  });

  it('survives unavailable sessionStorage', async () => {
    const { auth } = setup({ storage: () => { throw new Error('denied'); } });
    expect(auth.idToken()).toBeNull();
    await expect(auth.signIn()).resolves.toBeUndefined();
  });
});

describe('config', () => {
  it('parses the dev flag and rejects bad shapes', () => {
    expect(parseConfig({ ...cfg, dev: true }).dev).toBe(true);
    expect(parseConfig(cfg).dev).toBeUndefined();
    expect(() => parseConfig({ region: 1 })).toThrow();
  });
});

describe('TurnstileWidget', () => {
  it('renders once, resolves the token and resets for the next', async () => {
    let cb: (t: string) => void = () => {};
    const api: TurnstileApi = {
      render: vi.fn((_c, o) => { cb = o.callback; return 'w1'; }),
      reset: vi.fn(),
    };
    const w = new TurnstileWidget({ siteKey: 'k', container: {} as HTMLElement, api });
    const p1 = w.token();
    await Promise.resolve(); await Promise.resolve();
    cb('t1');
    expect(await p1).toBe('t1');
    const p2 = w.token();
    await Promise.resolve(); await Promise.resolve();
    expect(api.reset).toHaveBeenCalledWith('w1');
    cb('t2');
    expect(await p2).toBe('t2');
    expect(api.render).toHaveBeenCalledTimes(1);
  });

  it('rejects on timeout and expired callbacks', async () => {
    for (const k of ['timeout-callback', 'expired-callback'] as const) {
      const api: TurnstileApi = { render: (_c, o) => { o[k]?.(); return 'w'; }, reset: () => {} };
      const w = new TurnstileWidget({ siteKey: 'k', container: {} as HTMLElement, api });
      await expect(w.token()).rejects.toThrow();
    }
  });

  it('rejects on error-callback', async () => {
    const api: TurnstileApi = {
      render: (_c, o) => { o['error-callback']('300'); return 'w'; },
      reset: () => {},
    };
    const w = new TurnstileWidget({ siteKey: 'k', container: {} as HTMLElement, api });
    await expect(w.token()).rejects.toThrow(/300/);
  });
});
