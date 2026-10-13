export interface Config {
  region: string;
  userPoolDomain: string;
  clientId: string;
  turnstileSiteKey: string;
  redirectUri: string;
  /** Dev only: the hello carries a dev identity instead of a Cognito token. */
  dev?: boolean;
}

let cached: Promise<Config> | null = null;

export function parseConfig(raw: unknown): Config {
  if (typeof raw !== 'object' || raw === null) throw new Error('config.json is not an object');
  const r = raw as Record<string, unknown>;
  const str = (k: string): string => {
    const v = r[k];
    if (typeof v !== 'string') throw new Error(`config.json: "${k}" must be a string`);
    return v;
  };
  const cfg: Config = {
    region: str('region'),
    userPoolDomain: str('userPoolDomain'),
    clientId: str('clientId'),
    turnstileSiteKey: str('turnstileSiteKey'),
    redirectUri: str('redirectUri'),
  };
  if (r['dev'] === true) cfg.dev = true;
  return cfg;
}

/** Loads /config.json once (a failed load is not cached). */
export function loadConfig(fetchImpl: typeof fetch = fetch): Promise<Config> {
  const p = (async () => {
    const res = await fetchImpl('/config.json', { cache: 'no-store' });
    if (!res.ok) throw new Error(`config.json: HTTP ${res.status}`);
    return parseConfig(await res.json());
  })();
  if (!cached) {
    cached = p;
    p.catch(() => { if (cached === p) cached = null; });
  }
  return cached;
}
