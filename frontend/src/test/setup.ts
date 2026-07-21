import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// @testing-library/react's auto-cleanup only registers itself when Vitest's
// `globals: true` is on; this project imports describe/it/expect explicitly
// instead, so without this every render() past the first in a file leaves
// its DOM mounted and later queries start matching multiple elements.
afterEach(() => {
  cleanup()
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
