import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { api } from '../../api/client'
import { hasAnyRole, useCurrentRole } from '../../auth/roles'
import type {
  MovementEntity,
  OrderEntity,
  OrderItemEntity,
  OrderStatus,
  ResourceEntity,
  ShipmentEntity,
  ShipmentStatus,
  UnitEntity,
  WarehouseEntity,
} from '../../api/entities'
import { useEntity } from '../../api/useEntity'
import { useLookup } from '../../api/lookups'
import Badge, { type BadgeTone } from '../../components/Badge'
import { FormBanner } from '../../components/form/fields'
import type { PageResponse } from '../../components/table/useServerTable'

const ORDER_STATUS_TONE: Record<OrderStatus, BadgeTone> = {
  CREATED: 'neutral',
  VALIDATED: 'warn',
  COMPLETED: 'ok',
  CANCELLED: 'critical',
}

const SHIPMENT_STATUS_TONE: Record<ShipmentStatus, BadgeTone> = {
  PLANNED: 'neutral',
  IN_TRANSIT: 'warn',
  DELIVERED: 'ok',
}

async function fetchByOrderId<T>(path: string, orderId: number): Promise<T[]> {
  const response = await api.get<PageResponse<T>>(path, { params: { orderId, page: 0, size: 100 } })
  return response.data.content
}

export default function OrderDetailPage() {
  const { id } = useParams()
  const role = useCurrentRole()
  const canEdit = hasAnyRole(role, ['ADMIN', 'OPERATOR'])
  const location = useLocation()
  const routerBanner = (location.state as { banner?: string } | null)?.banner ?? null

  const { data: order, loading, notFound } = useEntity<OrderEntity>(`/orders/${id}`)
  const { data: unit } = useEntity<UnitEntity>(order ? `/units/${order.unitId}` : null)
  const { byId: resources } = useLookup<ResourceEntity>('/resources')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')

  const [items, setItems] = useState<OrderItemEntity[]>([])
  const [shipments, setShipments] = useState<ShipmentEntity[]>([])
  const [movements, setMovements] = useState<MovementEntity[]>([])

  useEffect(() => {
    if (!order) return
    let cancelled = false
    Promise.all([
      fetchByOrderId<OrderItemEntity>('/order-items', order.id),
      fetchByOrderId<ShipmentEntity>('/shipments', order.id),
      fetchByOrderId<MovementEntity>('/movements', order.id),
    ]).then(([itemsResult, shipmentsResult, movementsResult]) => {
      if (cancelled) return
      setItems(itemsResult)
      setShipments(shipmentsResult)
      setMovements(movementsResult)
    })
    return () => {
      cancelled = true
    }
  }, [order])

  if (loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading order…</p>
  }
  if (notFound || !order) {
    return <FormBanner message="Order not found." />
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">Order #{order.id}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {unit?.name ?? `Unit #${order.unitId}`} • {order.dateCreated} •{' '}
            <Badge tone={ORDER_STATUS_TONE[order.status]}>{order.status}</Badge>
          </p>
        </div>
        <div className="flex gap-3">
          <Link to="/orders" className="text-sm font-medium text-army-700 underline dark:text-army-300">
            Back
          </Link>
          {canEdit && (
            <Link to={`/orders/${order.id}/edit`} className="text-sm font-medium text-army-700 underline dark:text-army-300">
              Edit
            </Link>
          )}
        </div>
      </div>

      <FormBanner message={routerBanner} />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900">
          <h2 className="mb-3 text-lg font-semibold">Items</h2>
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
              <tr>
                <th className="py-2 pr-3 font-medium">Resource</th>
                <th className="py-2 text-right font-medium">Qty</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 && (
                <tr>
                  <td colSpan={2} className="py-3 text-center text-gray-400 dark:text-gray-500">
                    No items.
                  </td>
                </tr>
              )}
              {items.map((item) => (
                <tr key={item.id} className="border-t border-gray-100 dark:border-gray-800">
                  <td className="py-2 pr-3">
                    <div className="font-medium">{resources[item.resourceId]?.name ?? `#${item.resourceId}`}</div>
                    {resources[item.resourceId] && (
                      <div className="text-xs text-gray-500 dark:text-gray-400">{resources[item.resourceId].type}</div>
                    )}
                  </td>
                  <td className="py-2 text-right font-medium">{item.quantity}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900">
          <h2 className="mb-3 text-lg font-semibold">Shipments</h2>
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
              <tr>
                <th className="py-2 pr-3 font-medium">ID</th>
                <th className="py-2 pr-3 font-medium">Warehouse</th>
                <th className="py-2 pr-3 font-medium">Status</th>
                <th className="py-2 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {shipments.length === 0 && (
                <tr>
                  <td colSpan={4} className="py-3 text-center text-gray-400 dark:text-gray-500">
                    No shipments.
                  </td>
                </tr>
              )}
              {shipments.map((shipment) => (
                <tr key={shipment.id} className="border-t border-gray-100 dark:border-gray-800">
                  <td className="py-2 pr-3 font-medium">#{shipment.id}</td>
                  <td className="py-2 pr-3">{warehouses[shipment.warehouseId]?.name ?? `#${shipment.warehouseId}`}</td>
                  <td className="py-2 pr-3">
                    <Badge tone={SHIPMENT_STATUS_TONE[shipment.status]}>{shipment.status}</Badge>
                  </td>
                  <td className="py-2 text-right">
                    <Link to={`/shipments/${shipment.id}`} className="text-army-700 underline dark:text-army-300">
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {canEdit && (
            <Link
              to={`/shipments/new?orderId=${order.id}`}
              className="mt-3 inline-block rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
            >
              New shipment
            </Link>
          )}
        </div>

        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900 xl:col-span-2">
          <h2 className="mb-1 text-lg font-semibold">Movements (linked)</h2>
          <p className="mb-3 text-xs text-gray-500 dark:text-gray-400">
            Only movements explicitly linked to this order are shown.
          </p>
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
              <tr>
                <th className="py-2 pr-3 font-medium">Date</th>
                <th className="py-2 pr-3 font-medium">Action</th>
                <th className="py-2 pr-3 text-right font-medium">Amount</th>
                <th className="py-2 pr-3 font-medium">Reason</th>
                <th className="py-2 text-right font-medium">Shipment</th>
              </tr>
            </thead>
            <tbody>
              {movements.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-3 text-center text-gray-400 dark:text-gray-500">
                    No linked movements.
                  </td>
                </tr>
              )}
              {movements.map((movement) => (
                <tr key={movement.id} className="border-t border-gray-100 dark:border-gray-800">
                  <td className="py-2 pr-3">{movement.dateTime.replace('T', ' ').slice(0, 16)}</td>
                  <td className="py-2 pr-3">
                    <Badge tone={movement.type === 'ENTRY' ? 'ok' : 'warn'}>{movement.type}</Badge>
                  </td>
                  <td className="py-2 pr-3 text-right font-medium">{movement.quantity}</td>
                  <td className="py-2 pr-3">{movement.reason ?? '—'}</td>
                  <td className="py-2 text-right">
                    {movement.shipmentId ? (
                      <Link to={`/shipments/${movement.shipmentId}`} className="text-army-700 underline dark:text-army-300">
                        #{movement.shipmentId}
                      </Link>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
