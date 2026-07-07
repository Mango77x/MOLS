import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { useCurrentRole } from '../../auth/roles'
import { api } from '../../api/client'
import type { UnitEntity } from '../../api/entities'
import DataTable from '../../components/table/DataTable'
import { ActionLink, DeleteAction } from '../../components/table/RowActions'
import { useServerTable } from '../../components/table/useServerTable'

export default function UnitsPage() {
  const role = useCurrentRole()
  const isAdmin = role === 'ADMIN'
  const [name, setName] = useState('')

  const table = useServerTable<UnitEntity>('/units', { name }, { field: 'id', desc: false })

  async function handleDelete(id: number) {
    await api.delete(`/units/${id}`)
    table.reload()
  }

  const columns: ColumnDef<UnitEntity, unknown>[] = [
    { id: 'id', header: 'ID', accessorKey: 'id', enableSorting: true },
    { id: 'name', header: 'Name', accessorKey: 'name', enableSorting: true },
    { id: 'location', header: 'Location', accessorFn: (row) => row.location ?? '—' },
  ]
  if (isAdmin) {
    columns.push({
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => (
        <div className="flex gap-3">
          <ActionLink href={`/ui/units/${row.original.id}/edit`}>Edit</ActionLink>
          <DeleteAction label="unit" onConfirm={() => handleDelete(row.original.id)} />
        </div>
      ),
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">Units</h1>
        {isAdmin && (
          <a
            href="/ui/units/new"
            className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
          >
            New unit
          </a>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Search by name…"
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
      </div>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage="No units to display"
        sort={table.sort}
        onSortChange={table.toggleSort}
        page={table.page}
        size={table.size}
        totalPages={table.totalPages}
        totalElements={table.totalElements}
        onPageChange={table.setPage}
        onSizeChange={table.setSize}
      />
    </div>
  )
}
