import { copyFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Plugin } from 'vite';
import { defineConfig } from 'vitest/config';

// csp.json is the single CSP source; the build copies it next to the bundle.
function copyCsp(): Plugin {
  let outDir = 'dist';
  let root = process.cwd();
  return {
    name: 'copy-csp-json',
    apply: 'build',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir);
      root = config.root;
    },
    closeBundle() {
      copyFileSync(resolve(root, 'csp.json'), resolve(outDir, 'csp.json'));
    },
  };
}

const gateway = 'http://localhost:8080';

export default defineConfig({
  plugins: [copyCsp()],
  server: {
    proxy: {
      '/ws': { target: gateway, ws: true },
      '/healthz': gateway,
      '/api': gateway,
      '/archive': gateway,
    },
  },
  test: {
    include: ['test/**/*.test.ts'],
  },
});
