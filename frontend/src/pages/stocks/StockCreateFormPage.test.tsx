import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { ResourceEntity, WarehouseEntity } from '../../api/entities'
import { ToastProvider } from '../../components/toast/ToastProvider'
import StockCreateFormPage from './StockCreateFormPage'

const resource: ResourceEntity = { id: 1, name: 'Fuel', type: 'Material', criticality: 'HIGH' }
const warehouse: WarehouseEntity = { id: 1, name: 'Main Warehouse', location: 'Madrid', latitude: null, longitude: null }

function renderPage() {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/resources') return Promise.resolve({ data: [resource] })
    if (url === '/warehouses') return Promise.resolve({ data: [warehouse] })
    throw new Error(`Unmocked GET ${url}`)
  })
  render(
    <ToastProvider>
      <MemoryRouter>
        <StockCreateFormPage />
      </MemoryRouter>
    </ToastProvider>,
  )
}

/** Sprint 12: pins the create success toast (see WarehouseFormPage.test.tsx for context). */
describe('StockCreateFormPage — success toast', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a toast after creating a stock record', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
    const user = userEvent.setup()
    renderPage()

    await user.selectOptions(await screen.findByLabelText('Resource'), '1')
    await user.selectOptions(screen.getByLabelText('Warehouse'), '1')
    await user.click(screen.getByRole('button', { name: /create/i }))

    expect(await screen.findByText('Stock record created.')).toBeInTheDocument()
  })
})
