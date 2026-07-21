import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { OrderEntity, OrderItemEntity, OrderStatus, UnitEntity, WarehouseEntity } from '../../api/entities'
import type { PageResponse } from '../../components/table/useServerTable'
import { ToastProvider } from '../../components/toast/ToastProvider'
import OrderEditFormPage from './OrderEditFormPage'

const unit: UnitEntity = { id: 1, name: 'Test Unit', location: 'Barcelona', latitude: null, longitude: null }
const warehouse: WarehouseEntity = {
  id: 1,
  name: 'Main Warehouse',
  location: 'Madrid',
  latitude: null,
  longitude: null,
}
const item: OrderItemEntity = {
  id: 1,
  orderId: 1,
  resourceId: 1,
  quantity: 5,
  deliveredQuantity: 0,
  remainingQuantity: 5,
}
const itemsPage: PageResponse<OrderItemEntity> = {
  content: [item],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
}

function order(status: OrderStatus): OrderEntity {
  return { id: 1, unitId: 1, warehouseId: 1, dateCreated: '2026-07-21', status }
}

function mockApiFor(status: OrderStatus) {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/orders/1') return Promise.resolve({ data: order(status) })
    if (url === '/units') return Promise.resolve({ data: [unit] })
    if (url === '/warehouses') return Promise.resolve({ data: [warehouse] })
    if (url === '/resources') return Promise.resolve({ data: [] })
    if (url === '/order-items') return Promise.resolve({ data: itemsPage })
    throw new Error(`Unmocked GET ${url}`)
  })
}

function renderPage() {
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/orders/1/edit']}>
        <Routes>
          <Route path="/orders/:id/edit" element={<OrderEditFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

/**
 * Regression coverage for the Sprint 10 order-lock fix: editing a
 * COMPLETED/CANCELLED order used to present a fully live status select
 * (including invalid transitions) and item Add/Update/Remove controls, all
 * of which failed on submit since the API rejects any change in that state.
 */
describe('OrderEditFormPage — terminal-state lock', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('disables the status select and hides item controls when COMPLETED', async () => {
    mockApiFor('COMPLETED')
    renderPage()

    const statusSelect = await screen.findByLabelText('Status')
    expect(statusSelect).toBeDisabled()

    expect(await screen.findByText(/its status can no longer change/i)).toBeInTheDocument()
    expect(screen.getByText(/items can no longer be changed/i)).toBeInTheDocument()

    // The item row is still visible (read-only) but its Update/Remove
    // controls and the "add item" form must be gone.
    await waitFor(() => expect(screen.getByText('5')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Update' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Resource')).not.toBeInTheDocument()
  })

  it('disables the status select and hides item controls when CANCELLED', async () => {
    mockApiFor('CANCELLED')
    renderPage()

    expect(await screen.findByLabelText('Status')).toBeDisabled()
    expect(screen.getByText(/its status can no longer change/i)).toBeInTheDocument()
  })

  it('leaves everything editable for a non-terminal order (regression guard)', async () => {
    mockApiFor('CREATED')
    renderPage()

    const statusSelect = await screen.findByLabelText('Status')
    expect(statusSelect).not.toBeDisabled()
    expect(screen.queryByText(/its status can no longer change/i)).not.toBeInTheDocument()

    // The add-item form should be present.
    expect(await screen.findByLabelText('Resource')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Update' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remove' })).toBeInTheDocument()
  })
})

/** Sprint 12: pins the update success toast (see WarehouseFormPage.test.tsx for context). */
describe('OrderEditFormPage — success toast', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a toast after saving order changes', async () => {
    mockApiFor('CREATED')
    const putSpy = vi.spyOn(api, 'put').mockResolvedValue({ data: order('CREATED') })
    const user = userEvent.setup()
    renderPage()

    await screen.findByLabelText('Status')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(putSpy).toHaveBeenCalled())
    expect(await screen.findByText('Order updated.')).toBeInTheDocument()
  })
})
