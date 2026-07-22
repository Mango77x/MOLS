import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { api } from '../../api/client'
import { hasAnyRole, useCurrentRole } from '../../auth/roles'
import type {
  MovementEntity,
  OrderEntity,
  OrderItemEntity,
  ResourceEntity,
  ShipmentEntity,
  ShipmentStatus,
  UnitEntity,
  VehicleEntity,
  WarehouseEntity,
} from '../../api/entities'
import { useEntity } from '../../api/useEntity'
import { useLookup } from '../../api/lookups'
import Badge, { type BadgeTone } from '../../components/Badge'
import { FormBanner } from '../../components/form/fields'
import type { PageResponse } from '../../components/table/useServerTable'
import {
  enumLabel,
  MOVEMENT_TYPE_LABELS,
  SHIPMENT_STATUS_LABELS,
  VEHICLE_STATUS_LABELS,
  VEHICLE_TYPE_LABELS,
} from '../../lib/enumLabels'

const STATUS_TONE: Record<ShipmentStatus, BadgeTone> = {
  PLANNED: 'neutral',
  IN_TRANSIT: 'warn',
  DELIVERED: 'ok',
}

async function fetchAll<T>(path: string, params: Record<string, string | number>): Promise<T[]> {
  const response = await api.get<PageResponse<T>>(path, { params: { ...params, page: 0, size: 100 } })
  return response.data.content
}

export default function ShipmentDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const role = useCurrentRole()
  const canEdit = hasAnyRole(role, ['ADMIN', 'OPERATOR'])

  const { data: shipment, loading, notFound } = useEntity<ShipmentEntity>(`/shipments/${id}`)
  const { data: order } = useEntity<OrderEntity>(shipment ? `/orders/${shipment.orderId}` : null)
  const { data: unit } = useEntity<UnitEntity>(order ? `/units/${order.unitId}` : null)
  const { data: vehicle } = useEntity<VehicleEntity>(shipment ? `/vehicles/${shipment.vehicleId}` : null)
  const { data: warehouse } = useEntity<WarehouseEntity>(shipment ? `/warehouses/${shipment.warehouseId}` : null)
  const { byId: resources } = useLookup<ResourceEntity>('/resources')

  const [items, setItems] = useState<OrderItemEntity[]>([])
  const [movements, setMovements] = useState<MovementEntity[]>([])
  const itemById = Object.fromEntries(items.map((item) => [item.id, item]))

  useEffect(() => {
    if (!shipment || !order) return
    let cancelled = false
    Promise.all([
      fetchAll<OrderItemEntity>('/order-items', { orderId: order.id }),
      fetchAll<MovementEntity>('/movements', { shipmentId: shipment.id }),
    ]).then(([itemsResult, movementsResult]) => {
      if (cancelled) return
      setItems(itemsResult)
      setMovements(movementsResult)
    })
    return () => {
      cancelled = true
    }
  }, [shipment, order])

  if (loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">{t('shipments.loading')}</p>
  }
  if (notFound || !shipment) {
    return <FormBanner message={t('shipments.notFound')} />
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">{t('shipments.shipmentNumber', { id: shipment.id })}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            <Badge tone={STATUS_TONE[shipment.status]}>{enumLabel(SHIPMENT_STATUS_LABELS, shipment.status)}</Badge>
            {order && ` • ${t('orders.orderNumber', { id: order.id })}`}
          </p>
        </div>
        <div className="flex gap-3">
          <Link to="/shipments" className="text-sm font-medium text-army-700 underline dark:text-army-300">
            {t('common.back')}
          </Link>
          {canEdit && (
            <Link
              to={`/shipments/${shipment.id}/edit`}
              className="text-sm font-medium text-army-700 underline dark:text-army-300"
            >
              {t('common.edit')}
            </Link>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900 xl:col-span-1">
          <h2 className="mb-3 text-lg font-semibold">{t('shipments.detail.context')}</h2>
          <div>
            <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">{t('shipments.order')}</p>
            <p className="font-medium">{order ? t('orders.orderNumber', { id: order.id }) : '—'}</p>
            {unit && <p className="text-xs text-gray-500 dark:text-gray-400">{unit.name}</p>}
          </div>
          <div className="mt-3">
            <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">
              {t('shipments.detail.warehouseOrigin')}
            </p>
            <p className="font-medium">{warehouse?.name ?? '—'}</p>
            {warehouse && <p className="text-xs text-gray-500 dark:text-gray-400">{warehouse.location}</p>}
          </div>
          <div className="mt-3">
            <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">{t('shipments.vehicle')}</p>
            <p className="font-medium">{vehicle ? `#${vehicle.id}` : '—'}</p>
            {vehicle && (
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {enumLabel(VEHICLE_TYPE_LABELS, vehicle.type)} • {enumLabel(VEHICLE_STATUS_LABELS, vehicle.status)}
              </p>
            )}
          </div>
        </div>

        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900">
          <h2 className="mb-3 text-lg font-semibold">{t('shipments.detail.itemsTitle')}</h2>
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
              <tr>
                <th className="py-2 pr-3 font-medium">{t('common.resource')}</th>
                <th className="py-2 text-right font-medium">{t('shipments.form.ordered')}</th>
                <th className="py-2 text-right font-medium">{t('shipments.detail.thisShipment')}</th>
              </tr>
            </thead>
            <tbody>
              {shipment.items.length === 0 && (
                <tr>
                  <td colSpan={3} className="py-3 text-center text-gray-400 dark:text-gray-500">
                    {t('shipments.detail.noItems')}
                  </td>
                </tr>
              )}
              {shipment.items.map((line) => {
                const orderItem = itemById[line.orderItemId]
                const resource = orderItem ? resources[orderItem.resourceId] : undefined
                return (
                  <tr key={line.id} className="border-t border-gray-100 dark:border-gray-800">
                    <td className="py-2 pr-3">
                      <div className="font-medium">{resource?.name ?? (orderItem ? `#${orderItem.resourceId}` : `item #${line.orderItemId}`)}</div>
                      {resource && <div className="text-xs text-gray-500 dark:text-gray-400">{resource.type}</div>}
                    </td>
                    <td className="py-2 text-right text-gray-500 dark:text-gray-400">{orderItem?.quantity ?? '—'}</td>
                    <td className="py-2 text-right font-medium">{line.quantity}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900 xl:col-span-2">
          <h2 className="mb-1 text-lg font-semibold">{t('shipments.detail.movementsTitle')}</h2>
          <p className="mb-3 text-xs text-gray-500 dark:text-gray-400">{t('shipments.detail.movementsHint')}</p>
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
              <tr>
                <th className="py-2 pr-3 font-medium">{t('orders.date')}</th>
                <th className="py-2 pr-3 font-medium">{t('orders.action')}</th>
                <th className="py-2 pr-3 text-right font-medium">{t('orders.amount')}</th>
                <th className="py-2 pr-3 font-medium">{t('common.reason')}</th>
                <th className="py-2 text-right font-medium">{t('shipments.detail.stock')}</th>
              </tr>
            </thead>
            <tbody>
              {movements.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-3 text-center text-gray-400 dark:text-gray-500">
                    {t('shipments.detail.noLinkedMovements')}
                  </td>
                </tr>
              )}
              {movements.map((movement) => (
                <tr key={movement.id} className="border-t border-gray-100 dark:border-gray-800">
                  <td className="py-2 pr-3">{movement.dateTime.replace('T', ' ').slice(0, 16)}</td>
                  <td className="py-2 pr-3">
                    <Badge tone={movement.type === 'ENTRY' ? 'ok' : 'warn'}>
                      {enumLabel(MOVEMENT_TYPE_LABELS, movement.type)}
                    </Badge>
                  </td>
                  <td className="py-2 pr-3 text-right font-medium">{movement.quantity}</td>
                  <td className="py-2 pr-3">{movement.reason ?? '—'}</td>
                  <td className="py-2 text-right text-gray-500 dark:text-gray-400">{movement.stockId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
