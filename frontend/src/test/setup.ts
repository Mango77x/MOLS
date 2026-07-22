import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'
import i18n from '../i18n'

// @testing-library/react's auto-cleanup only registers itself when Vitest's
// `globals: true` is on; this project imports describe/it/expect explicitly
// instead, so without this every render() past the first in a file leaves
// its DOM mounted and later queries start matching multiple elements.
afterEach(() => {
  cleanup()
})

// i18next defaults to English (see src/i18n/index.ts), so every existing
// `getByRole(..., { name: 'Delete' })`-style assertion keeps passing
// unchanged — `t('common.delete')` resolves to the same "Delete" those
// tests already expect. Reset after each test in case one switches
// language (e.g. testing the language switcher itself) so that doesn't
// leak into the next test in the same file.
afterEach(() => {
  void i18n.changeLanguage('en')
})

// jsdom doesn't implement matchMedia; useTheme() calls it unconditionally on
// mount to pick a default theme, so anything rendering AppLayout needs a stub.
window.matchMedia ??= (query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: () => {},
  removeListener: () => {},
  addEventListener: () => {},
  removeEventListener: () => {},
  dispatchEvent: () => false,
})
