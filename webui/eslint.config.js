import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/', 'node_modules/', 'e2e-results/', 'test-results/', 'playwright-report/'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
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
