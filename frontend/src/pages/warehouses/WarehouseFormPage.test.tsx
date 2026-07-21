import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { WarehouseEntity } from '../../api/entities'
import type { PageResponse } from '../../components/table/useServerTable'
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

const emptyPage: PageResponse<WarehouseEntity> = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

function renderCreate(nameLookupResult: PageResponse<WarehouseEntity> = emptyPage) {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/warehouses') return Promise.resolve({ data: nameLookupResult })
    throw new Error(`Unmocked GET ${url}`)
  })
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

function renderEdit(nameLookupResult: PageResponse<WarehouseEntity> = emptyPage) {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/warehouses/1') return Promise.resolve({ data: warehouse })
    if (url === '/warehouses') return Promise.resolve({ data: nameLookupResult })
    throw new Error(`Unmocked GET ${url}`)
  })
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

/**
 * Sprint 14: there's no uniqueness constraint on warehouse names, so this
 * is a non-blocking nudge only — it must never stop a submit.
 */
describe('WarehouseFormPage — duplicate-name warning', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('warns on blur when another warehouse already has that name', async () => {
    const user = userEvent.setup()
    renderCreate({ content: [warehouse], page: 0, size: 20, totalElements: 1, totalPages: 1 })

    await user.type(screen.getByLabelText('Name'), warehouse.name)
    await user.click(screen.getByLabelText('Location'))

    expect(await screen.findByText(`A warehouse named "${warehouse.name}" already exists.`)).toBeInTheDocument()
  })

  it('does not warn when editing a warehouse against its own name', async () => {
    const user = userEvent.setup()
    renderEdit({ content: [warehouse], page: 0, size: 20, totalElements: 1, totalPages: 1 })

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Main Warehouse'))
    await user.click(screen.getByLabelText('Name'))
    await user.click(screen.getByLabelText('Location'))

    expect(screen.queryByText(/already exists/)).not.toBeInTheDocument()
  })

  it('does not block submit when the name looks like a duplicate', async () => {
    const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: { ...warehouse, id: 2 } })
    const user = userEvent.setup()
    renderCreate({ content: [warehouse], page: 0, size: 20, totalElements: 1, totalPages: 1 })

    await user.type(screen.getByLabelText('Name'), warehouse.name)
    await user.type(screen.getByLabelText('Location'), 'Valencia')
    await screen.findByText(`A warehouse named "${warehouse.name}" already exists.`)
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(postSpy).toHaveBeenCalled())
  })
})
