import { readFileSync } from "node:fs";

/**
 * The Content-Security-Policy CloudFront sends. `webui/csp.json` is the single
 * source: the dev gateway serves the same policy from the built UI.
 */
const file = new URL("../../webui/csp.json", import.meta.url);
const parsed = JSON.parse(readFileSync(file, "utf8")) as { csp?: unknown };
if (typeof parsed.csp !== "string" || parsed.csp.length === 0) {
  throw new Error(`${file.pathname}: expected {"csp": "<policy>"}`);
}

export const CSP: string = parsed.csp;
