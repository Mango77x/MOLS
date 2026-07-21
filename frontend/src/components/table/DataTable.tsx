import { flexRender, getCoreRowModel, useReactTable, type ColumnDef } from '@tanstack/react-table'
import { Fragment, type ReactNode } from 'react'
import type { SortState } from './useServerTable'

interface DataTableProps<T> {
  columns: ColumnDef<T, any>[]
  data: T[]
  loading: boolean
  error: boolean
  emptyMessage?: string
  sort?: SortState
  onSortChange?: (field: string) => void
  page: number
  size: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
  /** Optional extra row rendered right after a given row (e.g. expandable details). */
  renderRowExtra?: (row: T) => ReactNode
}

const PAGE_SIZES = [10, 20, 50, 100]

export default function DataTable<T>({
  columns,
  data,
  loading,
  error,
  emptyMessage = 'No records to display',
  sort,
  onSortChange,
  page,
  size,
  totalPages,
  totalElements,
  onPageChange,
  onSizeChange,
  renderRowExtra,
}: DataTableProps<T>) {
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  const headers = table.getHeaderGroups()[0]?.headers ?? []

  return (
    <div className="rounded-xl bg-white shadow-sm dark:bg-gray-900">
      {/* Card list — mobile only. Same columns/data as the table below, one
          labeled row per column instead of a <td>, so the actions column
          never scrolls out of view on a narrow viewport. */}
      <div className="divide-y divide-gray-100 sm:hidden dark:divide-gray-800" data-testid="data-table-cards">
        {loading &&
          Array.from({ length: 3 }, (_, rowIndex) => (
            <div key={`skeleton-card-${rowIndex}`} aria-hidden="true" className="space-y-2 p-3">
              {columns.map((_, colIndex) => (
                <div key={colIndex} className="h-4 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
              ))}
            </div>
          ))}
        {!loading && error && (
          <p className="px-3 py-6 text-center text-status-critical">Could not load data. Please try again.</p>
        )}
        {!loading && !error && data.length === 0 && (
          <p className="px-3 py-6 text-center text-gray-400 dark:text-gray-500">{emptyMessage}</p>
        )}
        {!loading &&
          !error &&
          table.getRowModel().rows.map((row) => (
            <Fragment key={row.id}>
              <div className="space-y-1 p-3">
                {row.getVisibleCells().map((cell, cellIndex) => (
                  <div key={cell.id} className="flex items-start justify-between gap-3 text-sm">
                    <span className="shrink-0 text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                      {headers[cellIndex]
                        ? flexRender(headers[cellIndex].column.columnDef.header, headers[cellIndex].getContext())
                        : null}
                    </span>
                    <span className="text-right">{flexRender(cell.column.columnDef.cell, cell.getContext())}</span>
                  </div>
                ))}
              </div>
              {renderRowExtra?.(row.original)}
            </Fragment>
          ))}
      </div>

      <div className="hidden overflow-x-auto sm:block">
        <table className="w-full text-left text-sm">
          <caption className="sr-only" aria-live="polite">
            {loading ? 'Loading…' : error ? 'Could not load data.' : `${totalElements} results`}
          </caption>
          <thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr
                key={headerGroup.id}
                className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400"
              >
                {headerGroup.headers.map((header) => {
                  const sortField = header.column.columnDef.enableSorting
                    ? header.column.id
                    : undefined
                  const active = sort?.field === sortField
                  return (
                    <th key={header.id} className="px-3 py-2 font-medium">
                      {sortField ? (
                        <button
                          type="button"
                          onClick={() => onSortChange?.(sortField)}
                          className="flex items-center gap-1 hover:text-gray-700 dark:hover:text-gray-200"
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          {active && <span>{sort?.desc ? '↓' : '↑'}</span>}
                        </button>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </th>
                  )
                })}
              </tr>
            ))}
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
            {loading &&
              Array.from({ length: 5 }, (_, rowIndex) => (
                <tr key={`skeleton-${rowIndex}`} aria-hidden="true">
                  {columns.map((_, colIndex) => (
                    // Decorative skeleton placeholder; the parent row is already aria-hidden, so
                    // there's nothing here for assistive tech to announce or need a label for.
                    // oxlint-disable-next-line jsx-a11y/control-has-associated-label
                    <td key={colIndex} className="px-3 py-2">
                      <div className="h-4 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
                    </td>
                  ))}
                </tr>
              ))}
            {!loading && error && (
              <tr>
                <td colSpan={columns.length} className="px-3 py-6 text-center text-status-critical">
                  Could not load data. Please try again.
                </td>
              </tr>
            )}
            {!loading && !error && data.length === 0 && (
              <tr>
                <td colSpan={columns.length} className="px-3 py-6 text-center text-gray-400 dark:text-gray-500">
                  {emptyMessage}
                </td>
              </tr>
            )}
            {!loading &&
              !error &&
              table.getRowModel().rows.map((row) => (
                <Fragment key={row.id}>
                  <tr>
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id} className="px-3 py-2 align-top">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                  {renderRowExtra?.(row.original)}
                </Fragment>
              ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 px-3 py-2 text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
        <div className="flex items-center gap-2">
          <span id="rows-per-page-label">Rows per page</span>
          <select
            aria-labelledby="rows-per-page-label"
            value={size}
            onChange={(e) => onSizeChange(Number(e.target.value))}
            className="rounded border border-gray-300 bg-white px-1 py-0.5 dark:border-gray-700 dark:bg-gray-800"
          >
            {PAGE_SIZES.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-center gap-3">
          <span>
            {totalElements === 0
              ? '0 results'
              : `${page * size + 1}–${Math.min((page + 1) * size, totalElements)} of ${totalElements}`}
          </span>
          <div className="flex gap-1">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => onPageChange(page - 1)}
              className="rounded border border-gray-300 px-2 py-0.5 disabled:opacity-40 dark:border-gray-700"
            >
              Prev
            </button>
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => onPageChange(page + 1)}
              className="rounded border border-gray-300 px-2 py-0.5 disabled:opacity-40 dark:border-gray-700"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
