import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { hasAnyRole, useCurrentRole } from '../../auth/roles'
import { api } from '../../api/client'
import { useLookup } from '../../api/lookups'
import type { ShipmentEntity, ShipmentStatus, VehicleEntity, WarehouseEntity } from '../../api/entities'
import Badge, { type BadgeTone } from '../../components/Badge'
import DataTable from '../../components/table/DataTable'
import { DeleteAction, RouteActionLink } from '../../components/table/RowActions'
import { useServerTable } from '../../components/table/useServerTable'
import { enumLabel, SHIPMENT_STATUS_LABELS, VEHICLE_TYPE_LABELS } from '../../lib/enumLabels'

const STATUS_TONE: Record<ShipmentStatus, BadgeTone> = {
  PLANNED: 'neutral',
  IN_TRANSIT: 'warn',
  DELIVERED: 'ok',
}

export default function ShipmentsPage() {
  const { t } = useTranslation()
  const role = useCurrentRole()
  const canEdit = hasAnyRole(role, ['ADMIN', 'OPERATOR'])
  const isAdmin = role === 'ADMIN'
  const [status, setStatus] = useState('')
  const [orderId, setOrderId] = useState('')

  const { byId: vehicles } = useLookup<VehicleEntity>('/vehicles')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')

  const table = useServerTable<ShipmentEntity>(
    '/shipments',
    { status, orderId },
    { field: 'id', desc: false },
  )

  async function handleDelete(id: number) {
    await api.delete(`/shipments/${id}`)
    table.reload()
  }

  const columns: ColumnDef<ShipmentEntity, unknown>[] = [
    { id: 'id', header: t('common.id'), accessorKey: 'id', enableSorting: true },
    { id: 'order', header: t('shipments.order'), accessorFn: (row) => `#${row.orderId}` },
    {
      id: 'vehicle',
      header: t('shipments.vehicle'),
      cell: ({ row }) => {
        const vehicle = vehicles[row.original.vehicleId]
        return vehicle ? `#${vehicle.id} — ${enumLabel(VEHICLE_TYPE_LABELS, vehicle.type)}` : `#${row.original.vehicleId}`
      },
    },
    {
      id: 'warehouse',
      header: t('common.warehouse'),
      cell: ({ row }) => warehouses[row.original.warehouseId]?.name ?? `#${row.original.warehouseId}`,
    },
    {
      id: 'status',
      header: t('common.status'),
      accessorKey: 'status',
      enableSorting: true,
      cell: ({ getValue }) => {
        const value = getValue<ShipmentStatus>()
        return <Badge tone={STATUS_TONE[value]}>{enumLabel(SHIPMENT_STATUS_LABELS, value)}</Badge>
      },
    },
    {
      id: 'actions',
      header: t('common.actions'),
      cell: ({ row }) => (
        <div className="flex gap-3">
          <RouteActionLink to={`/shipments/${row.original.id}`}>{t('shipments.view')}</RouteActionLink>
          {canEdit && <RouteActionLink to={`/shipments/${row.original.id}/edit`}>{t('common.edit')}</RouteActionLink>}
          {isAdmin && (
            <DeleteAction label={t('shipments.entityName')} onConfirm={() => handleDelete(row.original.id)} />
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">{t('shipments.title')}</h1>
        {canEdit && (
          <Link
            to="/shipments/new"
            className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
          >
            {t('shipments.newShipment')}
          </Link>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="">{t('common.allStatuses')}</option>
          {Object.entries(SHIPMENT_STATUS_LABELS).map(([value]) => (
            <option key={value} value={value}>
              {enumLabel(SHIPMENT_STATUS_LABELS, value)}
            </option>
          ))}
        </select>
        <input
          value={orderId}
          onChange={(e) => setOrderId(e.target.value)}
          placeholder={t('shipments.orderIdPlaceholder')}
          inputMode="numeric"
          className="w-28 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
      </div>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage={t('shipments.emptyMessage')}
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
