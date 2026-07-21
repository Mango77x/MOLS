import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { ResourceEntity, StockEntity, WarehouseEntity } from '../../api/entities'
import RecentActivity from './RecentActivity'
import type { RecentMovement } from './types'

const stock: StockEntity = { id: 2, resourceId: 5, warehouseId: 7, quantity: 10, reservedQuantity: 0 }
const resource: ResourceEntity = { id: 5, name: 'Botiquín Táctico', type: 'Medical', criticality: 'HIGH' }
const warehouse: WarehouseEntity = { id: 7, name: 'Almacén Norte', location: 'Burgos', latitude: null, longitude: null }

const movement: RecentMovement = {
  id: 1,
  stockId: 2,
  type: 'EXIT',
  quantity: 3,
  dateTime: '2026-07-21T17:20:00Z',
  orderId: null,
  shipmentId: 4,
  reason: 'Shipment delivered',
  createdBy: 'admin',
}

function renderWithLookups(movements: RecentMovement[]) {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/stocks') return Promise.resolve({ data: [stock] })
    if (url === '/resources') return Promise.resolve({ data: [resource] })
    if (url === '/warehouses') return Promise.resolve({ data: [warehouse] })
    throw new Error(`Unmocked GET ${url}`)
  })
  return render(<RecentActivity movements={movements} />)
}

/**
 * Sprint 13: RecentActivity used to render the raw stock id (#{stockId})
 * instead of a name, unlike the dashboard's own "Low stock" panel which
 * already resolves names server-side. Mirrors MovementsPage.tsx's
 * client-side resourceName/warehouseName lookup pattern instead of
 * changing the API contract.
 */
describe('RecentActivity — resource/warehouse name resolution', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders the resolved resource and warehouse names instead of a raw stock id', async () => {
    renderWithLookups([movement])

    expect(await screen.findByText('Botiquín Táctico')).toBeInTheDocument()
    expect(screen.getByText('Almacén Norte')).toBeInTheDocument()
    expect(screen.queryByText('#2')).not.toBeInTheDocument()
  })

  it('falls back to a labeled placeholder when the stock lookup has no match', async () => {
    renderWithLookups([{ ...movement, stockId: 999 }])

    expect(await screen.findByText('stock #999')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('shows the empty-state message when there are no movements', () => {
    renderWithLookups([])

    expect(screen.getByText('No recent activity to display')).toBeInTheDocument()
  })
})
