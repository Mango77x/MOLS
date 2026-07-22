import { defineConfig } from 'i18next-cli'

/**
 * Key-extraction tooling for `t('some.key')` calls across `src/`. English
 * (`src/i18n/en.json`) is the source of truth; run `npx i18next-cli extract
 * --sync-primary` after adding new `t()` calls to pick up new keys, then
 * translate the same keys into `es.json`/`fr.json` by hand (or hand
 * `en.json` to a translator/agency — that's the whole point of extracting
 * into a single source file instead of re-scanning the codebase each time).
 */
export default defineConfig({
  locales: ['en', 'es', 'fr'],
  extract: {
    input: ['src/**/*.{ts,tsx}', '!src/**/*.test.{ts,tsx}'],
    output: 'src/i18n/{{language}}.json',
    ignoredAttributes: ['className', 'aria-hidden'],
  },
})
