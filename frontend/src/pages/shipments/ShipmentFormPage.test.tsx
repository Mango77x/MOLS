import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { OrderEntity, OrderItemEntity, ResourceEntity, VehicleEntity, WarehouseEntity } from '../../api/entities'
import type { PageResponse } from '../../components/table/useServerTable'
import { ToastProvider } from '../../components/toast/ToastProvider'
import ShipmentFormPage from './ShipmentFormPage'

const order: OrderEntity = { id: 1, unitId: 1, warehouseId: 1, dateCreated: '2026-07-21', status: 'VALIDATED' }
const vehicle: VehicleEntity = { id: 1, type: 'LAND', capacity: 1000, status: 'AVAILABLE' }
const warehouse: WarehouseEntity = { id: 1, name: 'Main Warehouse', location: 'Madrid', latitude: null, longitude: null }
const resource: ResourceEntity = { id: 1, name: 'Fuel', type: 'Material', criticality: 'HIGH' }
const item: OrderItemEntity = { id: 1, orderId: 1, resourceId: 1, quantity: 5, deliveredQuantity: 0, remainingQuantity: 5 }
const itemsPage: PageResponse<OrderItemEntity> = { content: [item], page: 0, size: 100, totalElements: 1, totalPages: 1 }

function renderCreate() {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/orders') return Promise.resolve({ data: [order] })
    if (url === '/vehicles') return Promise.resolve({ data: [vehicle] })
    if (url === '/warehouses') return Promise.resolve({ data: [warehouse] })
    if (url === '/resources') return Promise.resolve({ data: [resource] })
    if (url === '/order-items') return Promise.resolve({ data: itemsPage })
    throw new Error(`Unmocked GET ${url}`)
  })
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/shipments/new']}>
        <Routes>
          <Route path="/shipments/new" element={<ShipmentFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

/** Sprint 12: pins the create success toast (see WarehouseFormPage.test.tsx for context). */
describe('ShipmentFormPage — success toast', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a toast after creating a shipment', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { id: 9 } })
    const user = userEvent.setup()
    renderCreate()

    await user.selectOptions(await screen.findByLabelText('Order'), '1')
    await user.selectOptions(screen.getByLabelText('Vehicle'), '1')
    await waitFor(() => expect(screen.getByLabelText(/quantity to ship for fuel/i)).toBeInTheDocument())
    await user.clear(screen.getByLabelText(/quantity to ship for fuel/i))
    await user.type(screen.getByLabelText(/quantity to ship for fuel/i), '2')
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByText('Shipment created.')).toBeInTheDocument()
  })
})
