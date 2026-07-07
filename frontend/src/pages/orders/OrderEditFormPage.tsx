import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { OrderEntity, UnitEntity, WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useLookup } from '../../api/lookups'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { positiveId } from '../../components/form/zodHelpers'
import OrderItemsManager from './OrderItemsManager'

const schema = z.object({
  unitId: positiveId('Select a unit'),
  dateCreated: z.string().min(1, 'Order creation date is required'),
  status: z.enum(['CREATED', 'VALIDATED', 'COMPLETED', 'CANCELLED'], { message: 'Select a status' }),
})

type FormValues = z.infer<typeof schema>

export default function OrderEditFormPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { data: order, loading, notFound } = useEntity<OrderEntity>(`/orders/${id}`)
  const { byId: units } = useLookup<UnitEntity>('/units')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  // Re-applies whenever the units lookup finishes loading too: the <select>'s
  // value only "sticks" once the matching <option> exists in the DOM, so
  // resetting only on `order` risks a silent no-op if the units lookup
  // (a separate async fetch) hasn't populated its options yet.
  useEffect(() => {
    if (order) {
      reset({ unitId: order.unitId, dateCreated: order.dateCreated, status: order.status })
    }
  }, [order, units, reset])

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      await api.put(`/orders/${id}`, values)
      navigate(`/orders/${id}`)
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading order…</p>
  }
  if (notFound || !order) {
    return <FormBanner message="Order not found." />
  }

  return (
    <FormPage title={`Edit order #${order.id}`} backTo={`/orders/${order.id}`} wide>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <SelectField
          label="Unit"
          id="unitId"
          defaultValue=""
          registration={register('unitId', { valueAsNumber: true })}
          error={errors.unitId?.message}
        >
          <option value="" disabled>
            Select a unit
          </option>
          {Object.values(units).map((u) => (
            <option key={u.id} value={u.id}>
              {u.name}
              {u.location ? ` — ${u.location}` : ''}
            </option>
          ))}
        </SelectField>
        <div>
          <span className="block text-sm font-medium text-gray-700 dark:text-gray-300">Origin warehouse</span>
          <p className="mt-1 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-600 dark:border-gray-800 dark:bg-gray-800/50 dark:text-gray-300">
            {warehouses[order.warehouseId]
              ? `${warehouses[order.warehouseId].name}${warehouses[order.warehouseId].location ? ` — ${warehouses[order.warehouseId].location}` : ''}`
              : '…'}
          </p>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            Fixed at creation — every item reserves stock from this warehouse.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField
            label="Date created"
            id="dateCreated"
            type="date"
            registration={register('dateCreated')}
            error={errors.dateCreated?.message}
          />
          <SelectField label="Status" id="status" registration={register('status')} error={errors.status?.message}>
            <option value="CREATED">Created</option>
            <option value="VALIDATED">Validated</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </SelectField>
        </div>
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save'}</SubmitButton>
          <SecondaryButton onClick={() => navigate(`/orders/${order.id}`)}>Cancel</SecondaryButton>
        </div>
      </form>

      <div className="mt-8 border-t border-gray-200 pt-6 dark:border-gray-800">
        <h2 className="mb-3 text-lg font-semibold">Items</h2>
        <OrderItemsManager orderId={order.id} />
      </div>
    </FormPage>
  )
}
