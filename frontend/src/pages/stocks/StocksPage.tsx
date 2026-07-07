import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCurrentRole } from '../../auth/roles'
import { api } from '../../api/client'
import { useLookup } from '../../api/lookups'
import type { ResourceEntity, StockEntity, WarehouseEntity } from '../../api/entities'
import Badge from '../../components/Badge'
import DataTable from '../../components/table/DataTable'
import { DeleteAction, RouteActionLink } from '../../components/table/RowActions'
import { useServerTable } from '../../components/table/useServerTable'

export default function StocksPage() {
  const role = useCurrentRole()
  const isAdmin = role === 'ADMIN'
  const [warehouseId, setWarehouseId] = useState('')
  const [resourceId, setResourceId] = useState('')

  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')
  const { byId: resources } = useLookup<ResourceEntity>('/resources')

  const table = useServerTable<StockEntity>(
    '/stocks',
    { warehouseId, resourceId },
    { field: 'id', desc: false },
  )

  async function handleDelete(id: number) {
    await api.delete(`/stocks/${id}`)
    table.reload()
  }

  const columns: ColumnDef<StockEntity, unknown>[] = [
    { id: 'id', header: 'ID', accessorKey: 'id', enableSorting: true },
    {
      id: 'resource',
      header: 'Resource',
      cell: ({ row }) => resources[row.original.resourceId]?.name ?? `#${row.original.resourceId}`,
    },
    {
      id: 'warehouse',
      header: 'Warehouse',
      cell: ({ row }) => warehouses[row.original.warehouseId]?.name ?? `#${row.original.warehouseId}`,
    },
    {
      id: 'quantity',
      header: 'Qty',
      accessorKey: 'quantity',
      enableSorting: true,
      cell: ({ getValue }) => {
        const value = getValue<number>()
        return <Badge tone={value === 0 ? 'neutral' : 'ok'}>{value}</Badge>
      },
    },
  ]
  if (isAdmin) {
    columns.push({
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => (
        <div className="flex gap-3">
          <RouteActionLink to={`/stocks/${row.original.id}/adjust`}>Adjust</RouteActionLink>
          <DeleteAction label="stock record" onConfirm={() => handleDelete(row.original.id)} />
        </div>
      ),
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">Stock</h1>
        {isAdmin && (
          <Link
            to="/stocks/new"
            className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
          >
            New stock record
          </Link>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <select
          value={warehouseId}
          onChange={(e) => setWarehouseId(e.target.value)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="">All warehouses</option>
          {Object.values(warehouses).map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
        <select
          value={resourceId}
          onChange={(e) => setResourceId(e.target.value)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="">All resources</option>
          {Object.values(resources).map((r) => (
            <option key={r.id} value={r.id}>
              {r.name}
            </option>
          ))}
        </select>
      </div>

      <p className="text-xs text-gray-400 dark:text-gray-500">
        Use Adjust to change a quantity — it always keeps the movement audit trail intact.
      </p>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage="No stock records to display"
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
