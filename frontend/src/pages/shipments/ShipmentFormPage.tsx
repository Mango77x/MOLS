import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { OrderEntity, OrderItemEntity, ResourceEntity, ShipmentEntity, VehicleEntity, WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useLookup } from '../../api/lookups'
import { useEntity } from '../../api/useEntity'
import type { PageResponse } from '../../components/table/useServerTable'
import { FormBanner, SecondaryButton, SelectField, SubmitButton } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { positiveId } from '../../components/form/zodHelpers'

const schema = z.object({
  orderId: positiveId('Select an order'),
  vehicleId: positiveId('Select a vehicle'),
  status: z.enum(['PLANNED', 'IN_TRANSIT', 'DELIVERED'], { message: 'Select a status' }),
})

type FormValues = z.infer<typeof schema>

export default function ShipmentFormPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const { data: shipment, loading, notFound } = useEntity<ShipmentEntity>(isEdit ? `/shipments/${id}` : null)
  const { byId: orders } = useLookup<OrderEntity>('/orders')
  const { byId: vehicles } = useLookup<VehicleEntity>('/vehicles')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')
  const { byId: resources } = useLookup<ResourceEntity>('/resources')
  const [banner, setBanner] = useState<string | null>(null)

  const [orderItems, setOrderItems] = useState<OrderItemEntity[]>([])
  const [itemQuantities, setItemQuantities] = useState<Record<number, number>>({})
  const [itemsError, setItemsError] = useState<string | null>(null)

  // Once delivered, items are locked (they already produced stock movements).
  const itemsLocked = isEdit && shipment?.status === 'DELIVERED'

  const openOrders = useMemo(
    () =>
      Object.values(orders).filter(
        (o) => o.status === 'CREATED' || o.status === 'VALIDATED' || o.status === 'PARTIALLY_SHIPPED',
      ),
    [orders],
  )
  const prefillOrderId = !isEdit ? Number(searchParams.get('orderId')) || undefined : undefined

  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      orderId: prefillOrderId,
      vehicleId: undefined,
      status: 'PLANNED',
    },
  })

  // Re-applies whenever a lookup finishes loading too: a <select>'s value only
  // "sticks" once the matching <option> exists in the DOM, so resetting only
  // on `shipment` risks a silent no-op if the order/vehicle lookups (separate
  // async fetches) haven't populated their options yet.
  useEffect(() => {
    if (shipment) {
      reset({
        orderId: shipment.orderId,
        vehicleId: shipment.vehicleId,
        status: shipment.status,
      })
    }
  }, [shipment, orders, vehicles, reset])

  // The origin warehouse is never chosen here — it's fixed on the order and
  // the shipment inherits it automatically, so the order's items and the
  // delivery deduction always agree on which warehouse. Just shown read-only.
  const selectedOrderId = watch('orderId')
  const originWarehouse = selectedOrderId ? warehouses[orders[selectedOrderId]?.warehouseId] : undefined

  // This shipment's own current line per order item — only known once both
  // the shipment (edit mode) and the order's items have loaded.
  const currentQuantityByItem = useMemo(() => {
    if (!isEdit || !shipment) return {} as Record<number, number>
    return Object.fromEntries(shipment.items.map((line) => [line.orderItemId, line.quantity]))
  }, [isEdit, shipment])

  useEffect(() => {
    if (!selectedOrderId) {
      setOrderItems([])
      setItemQuantities({})
      return
    }
    let cancelled = false
    void (async () => {
      const response = await api.get<PageResponse<OrderItemEntity>>('/order-items', {
        params: { orderId: selectedOrderId, page: 0, size: 100 },
      })
      if (cancelled) return
      setOrderItems(response.data.content)
      setItemQuantities((current) => {
        const next = { ...current }
        for (const item of response.data.content) {
          if (next[item.id] !== undefined) continue
          const ownCurrent = currentQuantityByItem[item.id] ?? 0
          // Own current allocation is excluded from remainingQuantity server-side,
          // so it must be added back to show the true remaining headroom for this shipment.
          next[item.id] = ownCurrent > 0 ? ownCurrent : item.remainingQuantity
        }
        return next
      })
    })()
    return () => {
      cancelled = true
    }
    // currentQuantityByItem intentionally excluded: it only matters for the initial seed above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedOrderId])

  function setItemQuantity(itemId: number, quantity: number) {
    setItemQuantities((current) => ({ ...current, [itemId]: quantity }))
  }

  async function onSubmit(values: FormValues) {
    setBanner(null)
    setItemsError(null)

    const items = orderItems
      .map((item) => ({ orderItemId: item.id, quantity: itemQuantities[item.id] ?? 0 }))
      .filter((line) => line.quantity > 0)

    if (!itemsLocked && items.length === 0) {
      setItemsError('Select at least one item to ship.')
      return
    }

    try {
      const payload = itemsLocked ? values : { ...values, items }
      if (isEdit) {
        await api.put(`/shipments/${id}`, payload)
        navigate(`/shipments/${id}`)
      } else {
        const response = await api.post<ShipmentEntity>('/shipments', payload)
        navigate(`/shipments/${response.data.id}`)
      }
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (isEdit && loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading shipment…</p>
  }
  if (isEdit && notFound) {
    return <FormBanner message="Shipment not found." />
  }

  return (
    <FormPage
      title={isEdit ? `Edit shipment #${id}` : 'New shipment'}
      backTo={isEdit ? `/shipments/${id}` : '/shipments'}
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <SelectField
          label="Order"
          id="orderId"
          defaultValue=""
          registration={register('orderId', { valueAsNumber: true })}
          error={errors.orderId?.message}
        >
          <option value="" disabled>
            Select an order
          </option>
          {(isEdit ? Object.values(orders) : openOrders).map((o) => (
            <option key={o.id} value={o.id}>
              Order #{o.id} — {o.dateCreated} — {o.status}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Vehicle"
          id="vehicleId"
          defaultValue=""
          registration={register('vehicleId', { valueAsNumber: true })}
          error={errors.vehicleId?.message}
        >
          <option value="" disabled>
            Select a vehicle
          </option>
          {Object.values(vehicles).map((v) => (
            <option key={v.id} value={v.id}>
              Vehicle #{v.id} — {v.type} — {v.status}
            </option>
          ))}
        </SelectField>
        <div>
          <span className="block text-sm font-medium text-gray-700 dark:text-gray-300">Warehouse (origin)</span>
          <p className="mt-1 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-600 dark:border-gray-800 dark:bg-gray-800/50 dark:text-gray-300">
            {originWarehouse
              ? `${originWarehouse.name}${originWarehouse.location ? ` — ${originWarehouse.location}` : ''}`
              : 'Select an order first'}
          </p>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Fixed to the selected order's warehouse.</p>
        </div>

        {!itemsLocked && (
          <div>
            <span className="block text-sm font-medium text-gray-700 dark:text-gray-300">Items to ship</span>
            {!selectedOrderId && (
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Select an order first.</p>
            )}
            {selectedOrderId && orderItems.length === 0 && (
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">This order has no items yet.</p>
            )}
            {orderItems.length > 0 && (
              <div className="mt-1 overflow-hidden rounded-lg border border-gray-200 dark:border-gray-800">
                <table className="w-full text-left text-sm">
                  <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500 dark:bg-gray-800/50 dark:text-gray-400">
                    <tr>
                      <th className="px-3 py-2 font-medium">Resource</th>
                      <th className="px-3 py-2 font-medium">Ordered</th>
                      <th className="px-3 py-2 font-medium">Delivered</th>
                      <th className="px-3 py-2 font-medium">Ship now</th>
                    </tr>
                  </thead>
                  <tbody>
                    {orderItems.map((item) => {
                      const ownCurrent = currentQuantityByItem[item.id] ?? 0
                      const max = item.remainingQuantity + ownCurrent
                      return (
                        <tr key={item.id} className="border-t border-gray-100 dark:border-gray-800">
                          <td className="px-3 py-2">{resources[item.resourceId]?.name ?? `#${item.resourceId}`}</td>
                          <td className="px-3 py-2">{item.quantity}</td>
                          <td className="px-3 py-2">{item.deliveredQuantity}</td>
                          <td className="px-3 py-2">
                            <input
                              type="number"
                              aria-label={`Quantity to ship for ${resources[item.resourceId]?.name ?? `resource #${item.resourceId}`}`}
                              min={0}
                              max={max}
                              value={itemQuantities[item.id] ?? 0}
                              onChange={(e) => setItemQuantity(item.id, Number(e.target.value))}
                              className="w-24 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
                            />
                            <span className="ml-1 text-xs text-gray-500 dark:text-gray-400">/ {max} left</span>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
            {itemsError && (
              <p role="alert" className="mt-1 text-sm text-status-critical">
                {itemsError}
              </p>
            )}
          </div>
        )}

        <SelectField label="Status" id="status" registration={register('status')} error={errors.status?.message}>
          <option value="PLANNED">Planned</option>
          <option value="IN_TRANSIT">In transit</option>
          <option value="DELIVERED">Delivered</option>
        </SelectField>
        {isEdit && (
          <p className="text-xs text-gray-500 dark:text-gray-400">
            Transitioning to Delivered deducts stock from the origin warehouse for this shipment's own items and
            records the movements — it will be rejected if stock is insufficient.
          </p>
        )}
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save'}</SubmitButton>
          <SecondaryButton onClick={() => navigate(isEdit ? `/shipments/${id}` : '/shipments')}>
            Cancel
          </SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
