import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../auth/store'
import AppLayout from './AppLayout'

function renderLayout() {
  useAuthStore.setState({ status: 'authenticated', user: { username: 'admin', role: 'ADMIN' } })
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<div>Page content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

/**
 * Sprint 16: verifies the language switcher end to end — the select
 * itself, useLocale's persistence/DOM-attribute wiring, and that nav
 * labels actually re-render in the newly chosen language. Everything
 * else in this sprint (individual `t()` calls) is covered by not
 * breaking the existing English-text assertions across the suite.
 */
describe('AppLayout — language switcher', () => {
  afterEach(() => {
    localStorage.removeItem('mols-locale')
  })

  it('renders nav labels in English by default', async () => {
    renderLayout()
    expect(await screen.findByRole('link', { name: 'Warehouses' })).toBeInTheDocument()
  })

  it('switching the language select re-renders nav labels in the chosen language', async () => {
    const user = userEvent.setup()
    renderLayout()

    await user.selectOptions(screen.getByRole('combobox'), 'fr')

    expect(await screen.findByRole('link', { name: 'Entrepôts' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Warehouses' })).not.toBeInTheDocument()
    expect(document.documentElement.lang).toBe('fr')
    expect(localStorage.getItem('mols-locale')).toBe('fr')
  })
})
