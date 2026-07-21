import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { UnitEntity } from '../../api/entities'
import { ToastProvider } from '../../components/toast/ToastProvider'
import UnitFormPage from './UnitFormPage'

vi.mock('../../components/map/LocationPicker', () => ({
  default: () => null,
}))

const unit: UnitEntity = { id: 1, name: '1st Battalion', location: 'Barcelona', latitude: null, longitude: null }

function renderCreate() {
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/units/new']}>
        <Routes>
          <Route path="/units/new" element={<UnitFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function renderEdit() {
  vi.spyOn(api, 'get').mockResolvedValue({ data: unit })
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/units/1/edit']}>
        <Routes>
          <Route path="/units/:id/edit" element={<UnitFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

/** Sprint 12: pins the create/update success toast (see WarehouseFormPage.test.tsx for context). */
describe('UnitFormPage — success toast', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a toast after creating a unit', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { ...unit, id: 2 } })
    const user = userEvent.setup()
    renderCreate()

    await user.type(screen.getByLabelText('Name'), '2nd Battalion')
    await user.type(screen.getByLabelText('Location'), 'Seville')
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByText('Unit created.')).toBeInTheDocument()
  })

  it('shows a toast after updating a unit', async () => {
    const putSpy = vi.spyOn(api, 'put').mockResolvedValue({ data: unit })
    const user = userEvent.setup()
    renderEdit()

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('1st Battalion'))
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(putSpy).toHaveBeenCalled())
    expect(await screen.findByText('Unit updated.')).toBeInTheDocument()
  })
})
