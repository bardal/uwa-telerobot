import { loadConfig, type Config } from '../config';

const KEY_STATE = 'telerobot.auth.state';
const KEY_VERIFIER = 'telerobot.auth.verifier';
const KEY_TOKENS = 'telerobot.auth.tokens';

export interface Tokens { idToken: string; accessToken?: string; refreshToken?: string }

export interface AuthDeps {
  config: () => Promise<Config | null>;
  fetchImpl: typeof fetch;
  storage: () => Storage | null;
  /** Current page URL (href) and navigation hooks. */
  location: () => { href: string };
  navigate: (url: string) => void;
  replaceUrl: (url: string) => void;
  now: () => number;
}

export function base64url(bytes: Uint8Array): string {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** 64 random bytes -> 86 base64url characters (RFC 7636: 43-128). */
export function generateVerifier(): string {
  return base64url(crypto.getRandomValues(new Uint8Array(64)));
}

export async function challengeFor(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64url(new Uint8Array(digest));
}

function jwtExp(jwt: string): number | null {
  try {
    const part = jwt.split('.')[1];
    if (!part) return null;
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    const exp = (JSON.parse(json) as { exp?: unknown }).exp;
    return typeof exp === 'number' ? exp : null;
  } catch {
    return null;
  }
}

export function createAuth(deps: AuthDeps) {
  let tokens: Tokens | null = null;

  const store = {
    get(k: string): string | null { try { return deps.storage()?.getItem(k) ?? null; } catch { return null; } },
    set(k: string, v: string): void { try { deps.storage()?.setItem(k, v); } catch { /* unavailable */ } },
    del(k: string): void { try { deps.storage()?.removeItem(k); } catch { /* unavailable */ } },
  };

  async function domainConfig(): Promise<Config> {
    const c = await deps.config();
    if (!c || !c.userPoolDomain || !c.clientId) {
      throw new Error('Sign-in is not configured (no Cognito user pool domain)');
    }
    return c;
  }

  function redirectUri(c: Config): string {
    return c.redirectUri || new URL('/', deps.location().href).toString();
  }

  function base(c: Config): string {
    return /^https?:\/\//.test(c.userPoolDomain) ? c.userPoolDomain : `https://${c.userPoolDomain}`;
  }

  function loadStored(): void {
    if (tokens) return;
    const raw = store.get(KEY_TOKENS);
    if (!raw) return;
    try {
      const t = JSON.parse(raw) as Tokens;
      if (typeof t.idToken === 'string') tokens = t;
    } catch { /* ignore */ }
  }

  return {
    /** Starts the Hosted UI redirect (authorization code + PKCE S256). Throws if sign-in is unconfigured. */
    async signIn(): Promise<void> {
      const c = await domainConfig();
      const verifier = generateVerifier();
      const state = base64url(crypto.getRandomValues(new Uint8Array(16)));
      store.set(KEY_VERIFIER, verifier);
      store.set(KEY_STATE, state);
      const q = new URLSearchParams({
        response_type: 'code',
        client_id: c.clientId,
        redirect_uri: redirectUri(c),
        scope: 'openid email profile',
        state,
        code_challenge_method: 'S256',
        code_challenge: await challengeFor(verifier),
      });
      deps.navigate(`${base(c)}/oauth2/authorize?${q.toString()}`);
    },

    /** Completes the code exchange if the URL carries ?code=&state=. Returns true when signed in by it. */
    async handleRedirect(): Promise<boolean> {
      const url = new URL(deps.location().href);
      const code = url.searchParams.get('code');
      const state = url.searchParams.get('state');
      if (!code || !state) return false;
      const expected = store.get(KEY_STATE);
      const verifier = store.get(KEY_VERIFIER);
      store.del(KEY_STATE);
      store.del(KEY_VERIFIER);
      url.searchParams.delete('code');
      url.searchParams.delete('state');
      deps.replaceUrl(url.pathname + url.search + url.hash);
      if (!expected || !verifier || expected !== state) return false;
      const c = await domainConfig();
      const res = await deps.fetchImpl(`${base(c)}/oauth2/token`, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'authorization_code',
          client_id: c.clientId,
          code,
          redirect_uri: redirectUri(c),
          code_verifier: verifier,
        }).toString(),
      });
      if (!res.ok) return false;
      const b = (await res.json()) as { id_token?: string; access_token?: string; refresh_token?: string };
      if (!b.id_token) return false;
      tokens = { idToken: b.id_token };
      if (b.access_token) tokens.accessToken = b.access_token;
      if (b.refresh_token) tokens.refreshToken = b.refresh_token;
      store.set(KEY_TOKENS, JSON.stringify(tokens));
      return true;
    },

    /** The current ID token, or null when signed out or expired. */
    idToken(): string | null {
      loadStored();
      if (!tokens) return null;
      const exp = jwtExp(tokens.idToken);
      if (exp !== null && exp * 1000 <= deps.now()) return null;
      return tokens.idToken;
    },

    async signOut(): Promise<void> {
      tokens = null;
      store.del(KEY_TOKENS);
      const c = await deps.config();
      if (!c || !c.userPoolDomain || !c.clientId) return;
      const q = new URLSearchParams({ client_id: c.clientId, logout_uri: redirectUri(c) });
      deps.navigate(`${base(c)}/logout?${q.toString()}`);
    },
  };
}

/** The page-wide instance. Callers should await loadConfig() before signIn(). */
export const auth = createAuth({
  config: () => loadConfig().catch(() => null),
  fetchImpl: (...a) => fetch(...a),
  storage: () => { try { return sessionStorage; } catch { return null; } },
  location: () => globalThis.location,
  navigate: (u) => { globalThis.location.assign(u); },
  replaceUrl: (u) => { globalThis.history.replaceState(null, '', u); },
  now: Date.now,
});
