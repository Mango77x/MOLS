import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { api } from './api/client'

function mockAuthenticatedApi() {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/auth/me') {
      return Promise.resolve({ data: { username: 'admin', role: 'ADMIN' } })
    }
    // A bare array satisfies both API contracts these routes' pages fetch
    // against: useLookup() iterates it directly, and useServerTable() reads
    // `.content ?? []`, which is happy with `undefined` on a plain array.
    return Promise.resolve({ data: [] })
  })
}

function renderAppAt(path: string) {
  window.history.pushState({}, '', path)
  return render(<App />)
}

/**
 * Regression coverage for the Sprint 10 routing fixes: an unmatched /app/*
 * path used to render a completely blank page, and the guessable /stock and
 * /audit-log slugs (the singular/hyphenated forms of the sidebar labels)
 * went nowhere at all.
 */
describe('App routing', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    window.history.pushState({}, '', '/app')
  })

  it('renders the 404 page for an unmatched path instead of a blank screen', async () => {
    mockAuthenticatedApi()
    renderAppAt('/app/does-not-exist')

    expect(await screen.findByText('Page not found')).toBeInTheDocument()
    expect(screen.getByText('/does-not-exist', { exact: false })).toBeInTheDocument()
  })

  it('redirects the guessable /stock slug to the real /stocks route', async () => {
    mockAuthenticatedApi()
    renderAppAt('/app/stock')

    await waitFor(() => expect(window.location.pathname).toBe('/app/stocks'))
    expect(screen.queryByText('Page not found')).not.toBeInTheDocument()
  })

  it('redirects the guessable /audit-log slug to the real /movements route', async () => {
    mockAuthenticatedApi()
    renderAppAt('/app/audit-log')

    await waitFor(() => expect(window.location.pathname).toBe('/app/movements'))
  })
})
