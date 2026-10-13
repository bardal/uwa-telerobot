import { defineConfig } from "vitest/config";

export default defineConfig({
  test: { include: ["lambda/**/*.test.ts", "test/**/*.test.ts"], testTimeout: 60_000, hookTimeout: 120_000 },
});
