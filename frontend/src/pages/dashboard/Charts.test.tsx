import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import Charts from './Charts'
import type { DashboardCharts } from './types'

const charts: DashboardCharts = {
  stockByWarehouse: { labels: ['Almacén Central'], values: [42] },
  movementsByType: { labels: ['ENTRY', 'EXIT'], values: [3, 1] },
  ordersByStatus: { labels: ['COMPLETED', 'CANCELLED'], values: [2, 1] },
}

/**
 * Sprint 13: the donut charts (movements-by-type, orders-by-status) only
 * identified slice colors via the Recharts Tooltip on hover — no way to
 * read them without a mouse, or on touch. Pins the fix: a plain-text
 * legend row (reusing the same color logic as the slices) always renders
 * alongside each donut, independent of hover.
 */
describe('Charts — donut legend', () => {
  it('shows a legend with each label and its value for both donut charts', () => {
    render(<Charts charts={charts} />)

    expect(screen.getByText('ENTRY (3)')).toBeInTheDocument()
    expect(screen.getByText('EXIT (1)')).toBeInTheDocument()
    expect(screen.getByText('COMPLETED (2)')).toBeInTheDocument()
    expect(screen.getByText('CANCELLED (1)')).toBeInTheDocument()
  })

  it('does not render a legend for an empty series (empty-state chart instead)', () => {
    render(
      <Charts
        charts={{
          ...charts,
          movementsByType: { labels: ['ENTRY', 'EXIT'], values: [0, 0] },
        }}
      />,
    )

    expect(screen.queryByText(/ENTRY/)).not.toBeInTheDocument()
  })
})
