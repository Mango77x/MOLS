import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { OrderEntity, ShipmentEntity, VehicleEntity, WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useLookup } from '../../api/lookups'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SelectField, SubmitButton } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { positiveId } from '../../components/form/zodHelpers'

const schema = z.object({
  orderId: positiveId('Select an order'),
  vehicleId: positiveId('Select a vehicle'),
  warehouseId: positiveId('Select a warehouse'),
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
  const [banner, setBanner] = useState<string | null>(null)

  const openOrders = useMemo(
    () => Object.values(orders).filter((o) => o.status === 'CREATED' || o.status === 'VALIDATED'),
    [orders],
  )
  const prefillOrderId = !isEdit ? Number(searchParams.get('orderId')) || undefined : undefined

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      orderId: prefillOrderId,
      vehicleId: undefined,
      warehouseId: undefined,
      status: 'PLANNED',
    },
  })

  // Re-applies whenever a lookup finishes loading too: a <select>'s value only
  // "sticks" once the matching <option> exists in the DOM, so resetting only
  // on `shipment` risks a silent no-op if the order/vehicle/warehouse lookups
  // (separate async fetches) haven't populated their options yet.
  useEffect(() => {
    if (shipment) {
      reset({
        orderId: shipment.orderId,
        vehicleId: shipment.vehicleId,
        warehouseId: shipment.warehouseId,
        status: shipment.status,
      })
    }
  }, [shipment, orders, vehicles, warehouses, reset])

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      if (isEdit) {
        await api.put(`/shipments/${id}`, values)
        navigate(`/shipments/${id}`)
      } else {
        const response = await api.post<ShipmentEntity>('/shipments', values)
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
        <SelectField
          label="Warehouse (origin)"
          id="warehouseId"
          defaultValue=""
          registration={register('warehouseId', { valueAsNumber: true })}
          error={errors.warehouseId?.message}
        >
          <option value="" disabled>
            Select a warehouse
          </option>
          {Object.values(warehouses).map((w) => (
            <option key={w.id} value={w.id}>
              {w.name} — {w.location}
            </option>
          ))}
        </SelectField>
        <SelectField label="Status" id="status" registration={register('status')} error={errors.status?.message}>
          <option value="PLANNED">Planned</option>
          <option value="IN_TRANSIT">In transit</option>
          <option value="DELIVERED">Delivered</option>
        </SelectField>
        {isEdit && (
          <p className="text-xs text-gray-500 dark:text-gray-400">
            Transitioning to Delivered deducts stock from the origin warehouse for each order item and
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
