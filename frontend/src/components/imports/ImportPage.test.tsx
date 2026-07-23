import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import type { CreateWarehouseRequest, ImportPreviewResponse } from '../../api/imports'
import { ToastProvider } from '../toast/ToastProvider'
import ImportPage, { type ImportColumn } from './ImportPage'

const columns: ImportColumn<CreateWarehouseRequest>[] = [
  { key: 'name', header: 'Name', render: (d) => d.name },
  { key: 'location', header: 'Location', render: (d) => d.location },
]

function renderPage() {
  return render(
    <ToastProvider>
      <MemoryRouter>
        <ImportPage
          title="Import warehouses"
          backTo="/warehouses"
          apiBasePath="/warehouses"
          columns={columns}
          csvColumnsHint="Expected columns: name, location"
        />
      </MemoryRouter>
    </ToastProvider>,
  )
}

function csvFile() {
  return new File(['name,location\nAlpha,Base A\n'], 'warehouses.csv', { type: 'text/csv' })
}

const mixedPreview: ImportPreviewResponse<CreateWarehouseRequest> = {
  rows: [
    { rowNumber: 1, status: 'VALID', errors: [], data: { name: 'Alpha', location: 'Base A', latitude: null, longitude: null } },
    { rowNumber: 2, status: 'DUPLICATE_WARNING', errors: [], data: { name: 'Beta', location: 'Base B', latitude: null, longitude: null } },
    { rowNumber: 3, status: 'ERROR', errors: ['Location is required'], data: null },
  ],
  validCount: 1,
  duplicateWarningCount: 1,
  errorCount: 1,
}

const allValidPreview: ImportPreviewResponse<CreateWarehouseRequest> = {
  rows: [
    { rowNumber: 1, status: 'VALID', errors: [], data: { name: 'Alpha', location: 'Base A', latitude: null, longitude: null } },
  ],
  validCount: 1,
  duplicateWarningCount: 0,
  errorCount: 0,
}

describe('ImportPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('disables Preview until a file is selected', () => {
    renderPage()

    expect(screen.getByRole('button', { name: /preview/i })).toBeDisabled()
  })

  it('renders every row with its status and posts to the preview endpoint', async () => {
    const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: mixedPreview })
    const user = userEvent.setup()
    renderPage()

    await user.upload(screen.getByLabelText(/csv file/i), csvFile())
    await user.click(screen.getByRole('button', { name: /preview/i }))

    expect(await screen.findByText('Alpha')).toBeInTheDocument()
    expect(screen.getByText('Beta')).toBeInTheDocument()
    expect(screen.getByText('Location is required')).toBeInTheDocument()
    expect(postSpy.mock.calls[0][0]).toBe('/warehouses/import/preview')
  })

  it('keeps Commit disabled while the preview has error rows', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: mixedPreview })
    const user = userEvent.setup()
    renderPage()

    await user.upload(screen.getByLabelText(/csv file/i), csvFile())
    await user.click(screen.getByRole('button', { name: /preview/i }))

    expect(await screen.findByRole('button', { name: /commit/i })).toBeDisabled()
  })

  it('enables Commit once every row is valid, and posts to the commit endpoint', async () => {
    const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: allValidPreview })
    const user = userEvent.setup()
    renderPage()

    await user.upload(screen.getByLabelText(/csv file/i), csvFile())
    await user.click(screen.getByRole('button', { name: /preview/i }))
    const commitButton = await screen.findByRole('button', { name: /commit 1 row/i })
    expect(commitButton).toBeEnabled()

    await user.click(commitButton)

    await waitFor(() => expect(postSpy).toHaveBeenCalledTimes(2))
    expect(postSpy.mock.calls[1][0]).toBe('/warehouses/import/commit')
    expect(await screen.findByText(/imported 1 row/i)).toBeInTheDocument()
  })

  it('disables Commit again after a successful commit, to prevent double-submitting the same file', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: allValidPreview })
    const user = userEvent.setup()
    renderPage()

    await user.upload(screen.getByLabelText(/csv file/i), csvFile())
    await user.click(screen.getByRole('button', { name: /preview/i }))
    await user.click(await screen.findByRole('button', { name: /commit 1 row/i }))

    expect(await screen.findByRole('button', { name: /commit 1 row/i })).toBeDisabled()
  })
})
