import { useEffect, useState } from 'react'
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
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading shipment…</p>
  }
  if (notFound || !shipment) {
    return <FormBanner message="Shipment not found." />
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">Shipment #{shipment.id}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            <Badge tone={STATUS_TONE[shipment.status]}>{shipment.status}</Badge>
            {order && ` • Order #${order.id}`}
          </p>
        </div>
        <div className="flex gap-3">
          <Link to="/shipments" className="text-sm font-medium text-army-700 underline dark:text-army-300">
            Back
          </Link>
          {canEdit && (
            <Link
              to={`/shipments/${shipment.id}/edit`}
              className="text-sm font-medium text-army-700 underline dark:text-army-300"
            >
              Edit
            </Link>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900 xl:col-span-1">
          <h2 className="mb-3 text-lg font-semibold">Context</h2>
          <div>
            <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">Order</p>
            <p className="font-medium">{order ? `Order #${order.id}` : '—'}</p>
            {unit && <p className="text-xs text-gray-500 dark:text-gray-400">{unit.name}</p>}
          </div>
          <div className="mt-3">
            <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">
              Warehouse (origin)
            </p>
            <p className="font-medium">{warehouse?.name ?? '—'}</p>
            {warehouse && <p className="text-xs text-gray-500 dark:text-gray-400">{warehouse.location}</p>}
          </div>
          <div className="mt-3">
            <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">Vehicle</p>
            <p className="font-medium">{vehicle ? `Vehicle #${vehicle.id}` : '—'}</p>
            {vehicle && (
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {vehicle.type} • {vehicle.status}
              </p>
            )}
          </div>
        </div>

        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900">
          <h2 className="mb-3 text-lg font-semibold">Order items</h2>
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

        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900 xl:col-span-2">
          <h2 className="mb-1 text-lg font-semibold">Movements (linked)</h2>
          <p className="mb-3 text-xs text-gray-500 dark:text-gray-400">
            Only movements explicitly linked to this shipment are shown.
          </p>
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
              <tr>
                <th className="py-2 pr-3 font-medium">Date</th>
                <th className="py-2 pr-3 font-medium">Action</th>
                <th className="py-2 pr-3 text-right font-medium">Amount</th>
                <th className="py-2 pr-3 font-medium">Reason</th>
                <th className="py-2 text-right font-medium">Stock</th>
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
