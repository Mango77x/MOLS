import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { hasAnyRole, useCurrentRole } from '../../auth/roles'
import { api } from '../../api/client'
import { useLookup } from '../../api/lookups'
import type { OrderEntity, OrderStatus, ResourceEntity, UnitEntity, WarehouseEntity } from '../../api/entities'
import Badge, { type BadgeTone } from '../../components/Badge'
import DataTable from '../../components/table/DataTable'
import { DeleteAction, RouteActionLink } from '../../components/table/RowActions'
import { useServerTable } from '../../components/table/useServerTable'
import OrderItemsRow from './OrderItemsRow'

const STATUS_TONE: Record<OrderStatus, BadgeTone> = {
  CREATED: 'neutral',
  VALIDATED: 'warn',
  PARTIALLY_SHIPPED: 'warn',
  COMPLETED: 'ok',
  CANCELLED: 'critical',
}

export default function OrdersPage() {
  const role = useCurrentRole()
  const canEdit = hasAnyRole(role, ['ADMIN', 'OPERATOR'])
  const isAdmin = role === 'ADMIN'
  const [status, setStatus] = useState('')
  const [unitId, setUnitId] = useState('')
  const [expanded, setExpanded] = useState<Set<number>>(new Set())

  const { byId: units } = useLookup<UnitEntity>('/units')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')
  const { byId: resources } = useLookup<ResourceEntity>('/resources')

  const table = useServerTable<OrderEntity>('/orders', { status, unitId }, { field: 'id', desc: false })

  async function handleDelete(id: number) {
    await api.delete(`/orders/${id}`)
    table.reload()
  }

  function toggleExpanded(id: number) {
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const columns: ColumnDef<OrderEntity, unknown>[] = [
    {
      id: 'id',
      header: 'ID',
      enableSorting: true,
      cell: ({ row }) => (
        <button
          type="button"
          onClick={() => toggleExpanded(row.original.id)}
          className="font-medium text-army-700 dark:text-army-300"
        >
          {expanded.has(row.original.id) ? '▾' : '▸'} #{row.original.id}
        </button>
      ),
    },
    {
      id: 'unit',
      header: 'Unit',
      cell: ({ row }) => units[row.original.unitId]?.name ?? `#${row.original.unitId}`,
    },
    {
      id: 'warehouse',
      header: 'Warehouse',
      cell: ({ row }) => warehouses[row.original.warehouseId]?.name ?? `#${row.original.warehouseId}`,
    },
    { id: 'dateCreated', header: 'Date created', accessorKey: 'dateCreated', enableSorting: true },
    {
      id: 'status',
      header: 'Status',
      accessorKey: 'status',
      enableSorting: true,
      cell: ({ getValue }) => {
        const value = getValue<OrderStatus>()
        return <Badge tone={STATUS_TONE[value]}>{value}</Badge>
      },
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => (
        <div className="flex gap-3">
          <RouteActionLink to={`/orders/${row.original.id}`}>View</RouteActionLink>
          {canEdit && <RouteActionLink to={`/orders/${row.original.id}/edit`}>Edit</RouteActionLink>}
          {isAdmin && (
            <DeleteAction label="order" onConfirm={() => handleDelete(row.original.id)} />
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">Orders</h1>
        {canEdit && (
          <Link
            to="/orders/new"
            className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
          >
            New order
          </Link>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="">All statuses</option>
          <option value="CREATED">CREATED</option>
          <option value="VALIDATED">VALIDATED</option>
          <option value="PARTIALLY_SHIPPED">PARTIALLY_SHIPPED</option>
          <option value="COMPLETED">COMPLETED</option>
          <option value="CANCELLED">CANCELLED</option>
        </select>
        <select
          value={unitId}
          onChange={(e) => setUnitId(e.target.value)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="">All units</option>
          {Object.values(units).map((u) => (
            <option key={u.id} value={u.id}>
              {u.name}
            </option>
          ))}
        </select>
      </div>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage="No orders to display"
        sort={table.sort}
        onSortChange={table.toggleSort}
        page={table.page}
        size={table.size}
        totalPages={table.totalPages}
        totalElements={table.totalElements}
        onPageChange={table.setPage}
        onSizeChange={table.setSize}
        renderRowExtra={(row) =>
          expanded.has(row.id) ? (
            <OrderItemsRow
              key={`items-${row.id}`}
              orderId={row.id}
              colSpan={columns.length}
              resources={resources}
            />
          ) : null
        }
      />
    </div>
  )
}
