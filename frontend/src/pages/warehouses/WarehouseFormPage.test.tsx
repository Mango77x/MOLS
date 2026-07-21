import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { WarehouseEntity } from '../../api/entities'
import { ToastProvider } from '../../components/toast/ToastProvider'
import WarehouseFormPage from './WarehouseFormPage'

vi.mock('../../components/map/LocationPicker', () => ({
  default: () => null,
}))

const warehouse: WarehouseEntity = {
  id: 1,
  name: 'Main Warehouse',
  location: 'Madrid',
  latitude: null,
  longitude: null,
}

function renderCreate() {
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/warehouses/new']}>
        <Routes>
          <Route path="/warehouses/new" element={<WarehouseFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function renderEdit() {
  vi.spyOn(api, 'get').mockResolvedValue({ data: warehouse })
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/warehouses/1/edit']}>
        <Routes>
          <Route path="/warehouses/:id/edit" element={<WarehouseFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

/**
 * Sprint 12: forms used to navigate away on success with zero feedback,
 * unlike RowActions' delete flow which already showed a toast. Pins the
 * fix — a success toast now fires right before the redirect.
 */
describe('WarehouseFormPage — success toast', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a toast after creating a warehouse', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { ...warehouse, id: 2 } })
    const user = userEvent.setup()
    renderCreate()

    await user.type(screen.getByLabelText('Name'), 'New Warehouse')
    await user.type(screen.getByLabelText('Location'), 'Valencia')
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByText('Warehouse created.')).toBeInTheDocument()
  })

  it('shows a toast after updating a warehouse', async () => {
    const putSpy = vi.spyOn(api, 'put').mockResolvedValue({ data: warehouse })
    const user = userEvent.setup()
    renderEdit()

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Main Warehouse'))
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(putSpy).toHaveBeenCalled())
    expect(await screen.findByText('Warehouse updated.')).toBeInTheDocument()
  })
})
