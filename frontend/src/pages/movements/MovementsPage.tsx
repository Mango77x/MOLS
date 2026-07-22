import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLookup } from '../../api/lookups'
import type {
  MovementEntity,
  MovementType,
  ResourceEntity,
  StockEntity,
  WarehouseEntity,
} from '../../api/entities'
import Badge, { type BadgeTone } from '../../components/Badge'
import DataTable from '../../components/table/DataTable'
import { useServerTable } from '../../components/table/useServerTable'

const TYPE_TONE: Record<MovementType, BadgeTone> = {
  ENTRY: 'ok',
  EXIT: 'warn',
}

// Locale passed explicitly (the active i18next language) so the date
// format always matches the UI language, rather than silently following
// whatever the browser happens to report — see RecentActivity.tsx's
// identical fix for the same underlying gap.
function formatDateTime(value: string, locale: string) {
  return new Date(value).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' })
}

export default function MovementsPage() {
  const { i18n } = useTranslation()
  const [type, setType] = useState('')
  const [orderId, setOrderId] = useState('')
  const [shipmentId, setShipmentId] = useState('')

  const { byId: stocks } = useLookup<StockEntity>('/stocks')
  const { byId: resources } = useLookup<ResourceEntity>('/resources')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')

  const table = useServerTable<MovementEntity>(
    '/movements',
    { type, orderId, shipmentId },
    { field: 'dateTime', desc: true },
  )

  function resourceName(stockId: number) {
    const stock = stocks[stockId]
    if (!stock) return `stock #${stockId}`
    return resources[stock.resourceId]?.name ?? `resource #${stock.resourceId}`
  }

  function warehouseName(stockId: number) {
    const stock = stocks[stockId]
    if (!stock) return '—'
    return warehouses[stock.warehouseId]?.name ?? `warehouse #${stock.warehouseId}`
  }

  const columns: ColumnDef<MovementEntity, unknown>[] = [
    {
      id: 'dateTime',
      header: 'Date',
      enableSorting: true,
      cell: ({ row }) => formatDateTime(row.original.dateTime, i18n.language),
    },
    {
      id: 'type',
      header: 'Type',
      accessorKey: 'type',
      enableSorting: true,
      cell: ({ getValue }) => {
        const value = getValue<MovementType>()
        return <Badge tone={TYPE_TONE[value]}>{value}</Badge>
      },
    },
    { id: 'quantity', header: 'Amount', accessorKey: 'quantity', enableSorting: true },
    { id: 'resource', header: 'Resource', cell: ({ row }) => resourceName(row.original.stockId) },
    { id: 'warehouse', header: 'Warehouse', cell: ({ row }) => warehouseName(row.original.stockId) },
    { id: 'reason', header: 'Reason', accessorFn: (row) => row.reason ?? '—' },
    { id: 'createdBy', header: 'By', accessorFn: (row) => row.createdBy ?? '—' },
    { id: 'orderId', header: 'Order', accessorFn: (row) => (row.orderId ? `#${row.orderId}` : '—') },
    {
      id: 'shipmentId',
      header: 'Shipment',
      accessorFn: (row) => (row.shipmentId ? `#${row.shipmentId}` : '—'),
    },
  ]

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Audit log</h1>

      <div className="flex flex-wrap gap-3">
        <select
          value={type}
          onChange={(e) => setType(e.target.value)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="">All types</option>
          <option value="ENTRY">ENTRY</option>
          <option value="EXIT">EXIT</option>
        </select>
        <input
          value={orderId}
          onChange={(e) => setOrderId(e.target.value)}
          placeholder="Order ID"
          inputMode="numeric"
          className="w-28 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
        <input
          value={shipmentId}
          onChange={(e) => setShipmentId(e.target.value)}
          placeholder="Shipment ID"
          inputMode="numeric"
          className="w-32 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
      </div>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage="No movements to display"
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
