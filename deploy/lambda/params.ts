/**
 * A getter for a setting that is either in the environment or in an SSM
 * parameter. The parameter is fetched on first use and cached for the life of
 * the execution environment; a failed fetch is not cached, so the next call
 * retries.
 */
export function cachedValue(
  envValue: string | undefined,
  paramName: string | undefined,
  fetch: (name: string) => Promise<string>,
): () => Promise<string> {
  let cached: Promise<string> | undefined;
  return () => {
    if (envValue) return Promise.resolve(envValue);
    if (!paramName) return Promise.resolve("");
    cached ??= fetch(paramName).catch((e) => {
      cached = undefined;
      throw e;
    });
    return cached;
  };
}

/**
 * The EventBridge schedule invokes the function with the constant input
 * `{"reap":true}`. Function URL requests always arrive wrapped (rawPath,
 * headers, body), so a caller cannot set a top-level `reap`.
 */
export function isReapEvent(event: object): boolean {
  const e = event as { reap?: unknown; source?: unknown; "detail-type"?: unknown };
  return e.reap === true || e.source === "aws.events" || e["detail-type"] === "Scheduled Event";
}
