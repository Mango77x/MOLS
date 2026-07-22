import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchAllPages } from '../../api/fetchAllPages'
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
import ExportCsvButton from '../../components/table/ExportCsvButton'
import { useServerTable } from '../../components/table/useServerTable'
import { downloadCsv, toCsv } from '../../lib/csv'
import { enumLabel, MOVEMENT_TYPE_LABELS } from '../../lib/enumLabels'

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
  const { t, i18n } = useTranslation()
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

  async function handleExport() {
    const rows = await fetchAllPages<MovementEntity>('/movements', { type, orderId, shipmentId }, table.sort)
    const csv = toCsv(
      [
        t('movements.date'),
        t('movements.type'),
        t('movements.amount'),
        t('common.resource'),
        t('common.warehouse'),
        t('common.reason'),
        t('movements.by'),
        t('shipments.order'),
        t('orders.shipment'),
      ],
      rows.map((m) => [
        formatDateTime(m.dateTime, i18n.language),
        enumLabel(MOVEMENT_TYPE_LABELS, m.type),
        String(m.quantity),
        resourceName(m.stockId),
        warehouseName(m.stockId),
        m.reason ?? '',
        m.createdBy ?? '',
        m.orderId ? `#${m.orderId}` : '',
        m.shipmentId ? `#${m.shipmentId}` : '',
      ]),
    )
    downloadCsv(`movements-${new Date().toISOString().slice(0, 10)}.csv`, csv)
  }

  const columns: ColumnDef<MovementEntity, unknown>[] = [
    {
      id: 'dateTime',
      header: t('movements.date'),
      enableSorting: true,
      cell: ({ row }) => formatDateTime(row.original.dateTime, i18n.language),
    },
    {
      id: 'type',
      header: t('movements.type'),
      accessorKey: 'type',
      enableSorting: true,
      cell: ({ getValue }) => {
        const value = getValue<MovementType>()
        return <Badge tone={TYPE_TONE[value]}>{enumLabel(MOVEMENT_TYPE_LABELS, value)}</Badge>
      },
    },
    { id: 'quantity', header: t('movements.amount'), accessorKey: 'quantity', enableSorting: true },
    { id: 'resource', header: t('common.resource'), cell: ({ row }) => resourceName(row.original.stockId) },
    { id: 'warehouse', header: t('common.warehouse'), cell: ({ row }) => warehouseName(row.original.stockId) },
    { id: 'reason', header: t('common.reason'), accessorFn: (row) => row.reason ?? '—' },
    { id: 'createdBy', header: t('movements.by'), accessorFn: (row) => row.createdBy ?? '—' },
    { id: 'orderId', header: t('shipments.order'), accessorFn: (row) => (row.orderId ? `#${row.orderId}` : '—') },
    {
      id: 'shipmentId',
      header: t('orders.shipment'),
      accessorFn: (row) => (row.shipmentId ? `#${row.shipmentId}` : '—'),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">{t('movements.title')}</h1>
        <ExportCsvButton onExport={handleExport} />
      </div>

      <div className="flex flex-wrap gap-3">
        <select
          value={type}
          onChange={(e) => setType(e.target.value)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="">{t('movements.allTypes')}</option>
          {Object.entries(MOVEMENT_TYPE_LABELS).map(([value]) => (
            <option key={value} value={value}>
              {enumLabel(MOVEMENT_TYPE_LABELS, value)}
            </option>
          ))}
        </select>
        <input
          value={orderId}
          onChange={(e) => setOrderId(e.target.value)}
          placeholder={t('movements.orderIdPlaceholder')}
          inputMode="numeric"
          className="w-28 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
        <input
          value={shipmentId}
          onChange={(e) => setShipmentId(e.target.value)}
          placeholder={t('movements.shipmentIdPlaceholder')}
          inputMode="numeric"
          className="w-32 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
      </div>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage={t('movements.emptyMessage')}
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
