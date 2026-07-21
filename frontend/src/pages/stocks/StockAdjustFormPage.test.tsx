import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { ResourceEntity, StockEntity, WarehouseEntity } from '../../api/entities'
import { ToastProvider } from '../../components/toast/ToastProvider'
import StockAdjustFormPage from './StockAdjustFormPage'

const stock: StockEntity = { id: 1, resourceId: 1, warehouseId: 1, quantity: 10, reservedQuantity: 0 }
const resource: ResourceEntity = { id: 1, name: 'Fuel', type: 'Material', criticality: 'HIGH' }
const warehouse: WarehouseEntity = { id: 1, name: 'Main Warehouse', location: 'Madrid', latitude: null, longitude: null }

function renderPage() {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/stocks/1') return Promise.resolve({ data: stock })
    if (url === '/resources/1') return Promise.resolve({ data: resource })
    if (url === '/warehouses/1') return Promise.resolve({ data: warehouse })
    throw new Error(`Unmocked GET ${url}`)
  })
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/stocks/1/adjust']}>
        <Routes>
          <Route path="/stocks/:id/adjust" element={<StockAdjustFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

/** Sprint 12: pins the success toast on a stock adjustment (see WarehouseFormPage.test.tsx for context). */
describe('StockAdjustFormPage — success toast', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a toast after applying a stock adjustment', async () => {
    const patchSpy = vi.spyOn(api, 'patch').mockResolvedValue({ data: stock })
    const user = userEvent.setup()
    renderPage()

    await waitFor(() => expect(screen.getByText('10')).toBeInTheDocument())
    await user.selectOptions(screen.getByLabelText('Action'), 'INCREASE')
    await user.type(screen.getByLabelText('Amount'), '5')
    await user.click(screen.getByRole('button', { name: /apply change/i }))

    await waitFor(() => expect(patchSpy).toHaveBeenCalled())
    expect(await screen.findByText('Stock adjusted.')).toBeInTheDocument()
  })
})
