import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import LoginPage from './LoginPage'

function mockSetupStatus(needsSetup: boolean) {
  return vi.spyOn(api, 'get').mockResolvedValue({ data: { needsSetup } })
}

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  )
}

/**
 * Regression coverage: a user who lost access (and isn't the one first-run
 * admin, so /app/setup isn't available) used to see nothing but "Invalid
 * username or password." with no path forward. This pins the static,
 * security-safe hint added to close that dead end.
 */
describe('LoginPage — lost-access hint', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a hint to contact the administrator once the login form renders', async () => {
    mockSetupStatus(false)
    renderLoginPage()

    expect(await screen.findByText(/contact your system administrator/i)).toBeInTheDocument()
  })

  it('does not show the hint while first-run setup is still needed (redirects to /setup instead)', async () => {
    mockSetupStatus(true)
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/setup" element={<div>SETUP PAGE</div>} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('SETUP PAGE')).toBeInTheDocument()
    expect(screen.queryByText(/contact your system administrator/i)).not.toBeInTheDocument()
  })
})
