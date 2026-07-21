import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { VehicleEntity } from '../../api/entities'
import { ToastProvider } from '../../components/toast/ToastProvider'
import VehicleFormPage from './VehicleFormPage'

const vehicle: VehicleEntity = { id: 1, type: 'LAND', capacity: 1000, status: 'AVAILABLE' }

function renderCreate() {
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/vehicles/new']}>
        <Routes>
          <Route path="/vehicles/new" element={<VehicleFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function renderEdit() {
  vi.spyOn(api, 'get').mockResolvedValue({ data: vehicle })
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/vehicles/1/edit']}>
        <Routes>
          <Route path="/vehicles/:id/edit" element={<VehicleFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

/** Sprint 12: pins the create/update success toast (see WarehouseFormPage.test.tsx for context). */
describe('VehicleFormPage — success toast', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a toast after creating a vehicle', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { ...vehicle, id: 2 } })
    const user = userEvent.setup()
    renderCreate()

    await user.selectOptions(screen.getByLabelText('Type'), 'SEA')
    await user.type(screen.getByLabelText('Capacity (kg)'), '500')
    await user.selectOptions(screen.getByLabelText('Status'), 'AVAILABLE')
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByText('Vehicle created.')).toBeInTheDocument()
  })

  it('shows a toast after updating a vehicle', async () => {
    const putSpy = vi.spyOn(api, 'put').mockResolvedValue({ data: vehicle })
    const user = userEvent.setup()
    renderEdit()

    await waitFor(() => expect((screen.getByLabelText('Type') as HTMLSelectElement).value).toBe('LAND'))
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(putSpy).toHaveBeenCalled())
    expect(await screen.findByText('Vehicle updated.')).toBeInTheDocument()
  })
})
