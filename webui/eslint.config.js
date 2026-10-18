import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/', 'node_modules/', 'e2e-results/', 'test-results/', 'playwright-report/'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // the e2e stack launcher and the build scripts are plain Node scripts
    files: ['e2e/**/*.mjs', 'scripts/**/*.mjs'],
    languageOptions: {
      globals: { process: 'readonly', console: 'readonly', fetch: 'readonly', setTimeout: 'readonly', URL: 'readonly', TextDecoder: 'readonly', Buffer: 'readonly' },
    },
  },
  {
    rules: {
      'no-restricted-properties': [
        'error',
        { property: 'innerHTML', message: 'Use textContent or DOM construction (CSP / XSS).' },
        { property: 'outerHTML', message: 'Use DOM construction (CSP / XSS).' },
        { property: 'insertAdjacentHTML', message: 'Use DOM construction (CSP / XSS).' },
      ],
      'no-restricted-syntax': [
        'error',
        {
          selector: "CallExpression[callee.object.name='document'][callee.property.name=/^(write|writeln)$/]",
          message: 'document.write is forbidden.',
        },
      ],
    },
  },
);
