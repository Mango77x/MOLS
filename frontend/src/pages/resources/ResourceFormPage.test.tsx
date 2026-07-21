import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { ResourceEntity } from '../../api/entities'
import type { PageResponse } from '../../components/table/useServerTable'
import { ToastProvider } from '../../components/toast/ToastProvider'
import ResourceFormPage from './ResourceFormPage'

const resource: ResourceEntity = { id: 1, name: 'Fuel', type: 'Material', criticality: 'HIGH' }
const emptyPage: PageResponse<ResourceEntity> = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

function renderCreate(nameLookupResult: PageResponse<ResourceEntity> = emptyPage) {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/resources') return Promise.resolve({ data: nameLookupResult })
    throw new Error(`Unmocked GET ${url}`)
  })
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/resources/new']}>
        <Routes>
          <Route path="/resources/new" element={<ResourceFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function renderEdit(nameLookupResult: PageResponse<ResourceEntity> = emptyPage) {
  vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/resources/1') return Promise.resolve({ data: resource })
    if (url === '/resources') return Promise.resolve({ data: nameLookupResult })
    throw new Error(`Unmocked GET ${url}`)
  })
  render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/resources/1/edit']}>
        <Routes>
          <Route path="/resources/:id/edit" element={<ResourceFormPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

/** Sprint 12: pins the create/update success toast (see WarehouseFormPage.test.tsx for context). */
describe('ResourceFormPage — success toast', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a toast after creating a resource', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { ...resource, id: 2 } })
    const user = userEvent.setup()
    renderCreate()

    await user.type(screen.getByLabelText('Name'), 'Generator')
    await user.type(screen.getByLabelText('Type'), 'Equipment')
    await user.selectOptions(screen.getByLabelText('Criticality'), 'MEDIUM')
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByText('Resource created.')).toBeInTheDocument()
  })

  it('shows a toast after updating a resource', async () => {
    const putSpy = vi.spyOn(api, 'put').mockResolvedValue({ data: resource })
    const user = userEvent.setup()
    renderEdit()

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Fuel'))
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(putSpy).toHaveBeenCalled())
    expect(await screen.findByText('Resource updated.')).toBeInTheDocument()
  })
})

/** Sprint 14: non-blocking duplicate-name nudge (see WarehouseFormPage.test.tsx for the full scenario coverage). */
describe('ResourceFormPage — duplicate-name warning', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('warns on blur when another resource already has that name', async () => {
    const user = userEvent.setup()
    renderCreate({ content: [resource], page: 0, size: 20, totalElements: 1, totalPages: 1 })

    await user.type(screen.getByLabelText('Name'), resource.name)
    await user.click(screen.getByLabelText('Type'))

    expect(await screen.findByText(`A resource named "${resource.name}" already exists.`)).toBeInTheDocument()
  })

  it('does not warn when editing a resource against its own name', async () => {
    const user = userEvent.setup()
    renderEdit({ content: [resource], page: 0, size: 20, totalElements: 1, totalPages: 1 })

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Fuel'))
    await user.click(screen.getByLabelText('Name'))
    await user.click(screen.getByLabelText('Type'))

    expect(screen.queryByText(/already exists/)).not.toBeInTheDocument()
  })
})
