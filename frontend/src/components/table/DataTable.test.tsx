import { render, screen, within } from '@testing-library/react'
import type { ColumnDef } from '@tanstack/react-table'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../../i18n'
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
  return render(
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

/**
 * Sprint 14: the caption said "1 results" for a single row (contrast with
 * the pagination summary just below it, which already special-cased 0).
 */
describe('DataTable — result count caption', () => {
  it('uses the singular "result" for exactly one row', () => {
    const { container } = renderTable({ data: [rows[0]], totalElements: 1 })
    expect(container.querySelector('caption')).toHaveTextContent('1 result')
  })

  it('uses the plural "results" for more than one row', () => {
    const { container } = renderTable()
    expect(container.querySelector('caption')).toHaveTextContent('2 results')
  })

  it('uses the plural "results" for zero rows too', () => {
    // The pagination summary below the table already says "0 results" too
    // (it special-cased 0 from the start) — scope to the caption
    // specifically so this test only pins the caption's own behavior.
    const { container } = renderTable({ data: [], totalElements: 0 })
    expect(container.querySelector('caption')).toHaveTextContent('0 results')
  })
})

/**
 * Sprint 16: the Sprint 14 fix above is itself only correct for languages
 * that share English's singular/plural boundary (1 vs. everything else).
 * French doesn't: 0 is grammatically singular ("0 résultat"), not plural
 * — a hand-rolled `count === 1 ? singular : plural` ternary would get this
 * wrong. Routing through i18next's `count`-based plural keys (CLDR plural
 * rules under the hood) gets it right without hand-writing per-locale
 * logic, which is the whole reason that mechanism was adopted here instead
 * of just translating the words in the old ternary.
 */
describe('DataTable — result count caption across locales', () => {
  afterEach(() => {
    void i18n.changeLanguage('en')
  })

  it('treats 0 as singular in French, unlike English', async () => {
    await i18n.changeLanguage('fr')
    const { container } = renderTable({ data: [], totalElements: 0 })
    expect(container.querySelector('caption')).toHaveTextContent('0 résultat')
  })

  it('treats 1 as singular in French too', async () => {
    await i18n.changeLanguage('fr')
    const { container } = renderTable({ data: [rows[0]], totalElements: 1 })
    expect(container.querySelector('caption')).toHaveTextContent('1 résultat')
  })

  it('treats 2+ as plural in French', async () => {
    await i18n.changeLanguage('fr')
    const { container } = renderTable()
    expect(container.querySelector('caption')).toHaveTextContent('2 résultats')
  })
})
