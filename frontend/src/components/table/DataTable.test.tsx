import { render, screen, within } from '@testing-library/react'
import type { ColumnDef } from '@tanstack/react-table'
import { describe, expect, it } from 'vitest'
import DataTable from './DataTable'

interface Row {
  id: number
  name: string
}

const columns: ColumnDef<Row, unknown>[] = [
  { id: 'id', header: 'ID', accessorKey: 'id' },
  { id: 'name', header: 'Name', accessorKey: 'name' },
]

const rows: Row[] = [
  { id: 1, name: 'Main Warehouse' },
  { id: 2, name: 'Secondary Warehouse' },
]

function renderTable(overrides: Partial<Parameters<typeof DataTable<Row>>[0]> = {}) {
  render(
    <DataTable<Row>
      columns={columns}
      data={rows}
      loading={false}
      error={false}
      page={0}
      size={20}
      totalPages={1}
      totalElements={rows.length}
      onPageChange={() => {}}
      onSizeChange={() => {}}
      {...overrides}
    />,
  )
}

/**
 * Sprint 12: DataTable.tsx:49 wrapped the table in overflow-x-auto only —
 * on mobile the actions column just scrolled out of view. jsdom doesn't
 * compute Tailwind's `sm:` breakpoints, so this can't assert visibility;
 * instead it pins that the card markup (a parallel, non-table rendering
 * path driven by the same columns/data) exists and carries the same rows,
 * alongside the standard <table> which real browsers hide below `sm` via
 * CSS and is therefore excluded from the accessibility tree there.
 */
describe('DataTable — mobile card view', () => {
  it('renders the same rows as labeled cards, scoped to the card container', () => {
    renderTable()

    const cards = screen.getByTestId('data-table-cards')
    expect(within(cards).getByText('Main Warehouse')).toBeInTheDocument()
    expect(within(cards).getByText('Secondary Warehouse')).toBeInTheDocument()
    // Each cell is labeled with its column header, not left as a bare value.
    expect(within(cards).getAllByText('Name').length).toBeGreaterThan(0)

    // The full table is still rendered (hidden below `sm` via CSS, not
    // removed from the DOM) so desktop viewports keep working unchanged.
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  it('shows the empty message in the card view when there is no data', () => {
    renderTable({ data: [], totalElements: 0 })

    const cards = screen.getByTestId('data-table-cards')
    expect(within(cards).getByText('No records to display')).toBeInTheDocument()
  })

  it('shows the error message in the card view on load failure', () => {
    renderTable({ error: true })

    const cards = screen.getByTestId('data-table-cards')
    expect(within(cards).getByText('Could not load data. Please try again.')).toBeInTheDocument()
  })
})
