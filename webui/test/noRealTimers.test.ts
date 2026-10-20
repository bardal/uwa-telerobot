import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

// Tests do not run on wall-clock time: vi.useFakeTimers() and vi.advanceTimersByTime*() move time,
// never a real setTimeout or setInterval in a test.
describe('the tests', () => {
  it('wait on fake timers, never real ones', () => {
    const dir = join(__dirname);
    const found: string[] = [];
    for (const f of readdirSync(dir).filter((n) => n.endsWith('.ts') && n !== 'noRealTimers.test.ts')) {
      readFileSync(join(dir, f), 'utf8').split('\n').forEach((line, i) => {
        if (/\b(setTimeout|setInterval)\s*\(/.test(line)) found.push(`${f}:${i + 1}: ${line.trim()}`);
      });
    }
    expect(found).toEqual([]);
  });
});
