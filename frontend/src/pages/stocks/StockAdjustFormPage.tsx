import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { ResourceEntity, StockEntity, WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { positiveNumber } from '../../components/form/zodHelpers'

const schema = z.object({
  operation: z.enum(['INCREASE', 'DECREASE'], { message: 'Select an action' }),
  amount: positiveNumber('Amount must be greater than 0'),
})

type FormValues = z.infer<typeof schema>

export default function StockAdjustFormPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { data: stock, loading, notFound } = useEntity<StockEntity>(`/stocks/${id}`)
  const { data: resource } = useEntity<ResourceEntity>(stock ? `/resources/${stock.resourceId}` : null)
  const { data: warehouse } = useEntity<WarehouseEntity>(stock ? `/warehouses/${stock.warehouseId}` : null)
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { operation: undefined, amount: undefined },
  })

  async function onSubmit(values: FormValues) {
    setBanner(null)
    const delta = values.operation === 'INCREASE' ? values.amount : -values.amount
    try {
      await api.patch(`/stocks/${id}/adjust`, { delta })
      navigate('/stocks')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading stock…</p>
  }
  if (notFound || !stock) {
    return <FormBanner message="Stock record not found." />
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">Adjust stock</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">Stock ID: {stock.id}</p>
        </div>
        <Link to="/stocks" className="text-sm font-medium text-army-700 underline dark:text-army-300">
          Back
        </Link>
      </div>

      <div className="flex flex-col gap-4 lg:flex-row">
        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900 lg:w-80">
          <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">Current</p>
          <div className="mt-2">
            <div className="font-semibold">{resource?.name ?? '—'}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400">
              {resource ? `${resource.type} • ${resource.criticality}` : '—'}
            </div>
          </div>
          <div className="mt-3">
            <div className="font-semibold">{warehouse?.name ?? '—'}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400">{warehouse?.location ?? '—'}</div>
          </div>
          <div className="mt-3">
            <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">Quantity</p>
            <div className="text-2xl font-semibold">{stock.quantity}</div>
          </div>
        </div>

        <div className="max-w-xl flex-1 rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <FormBanner message={banner} />
            <SelectField
              label="Action"
              id="operation"
              registration={register('operation')}
              error={errors.operation?.message}
              defaultValue=""
            >
              <option value="" disabled>
                Select an action
              </option>
              <option value="INCREASE">Increase stock</option>
              <option value="DECREASE">Decrease stock</option>
            </SelectField>
            <TextField
              label="Amount"
              id="amount"
              type="number"
              min={1}
              step={1}
              placeholder="e.g. 5"
              registration={register('amount', { valueAsNumber: true })}
              error={errors.amount?.message}
            />
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Every change is recorded in the audit log (movement history). Decreases cannot go below
              zero.
            </p>
            <div className="flex gap-3">
              <SubmitButton submitting={isSubmitting}>
                {isSubmitting ? 'Applying…' : 'Apply change'}
              </SubmitButton>
              <SecondaryButton onClick={() => navigate('/stocks')}>Cancel</SecondaryButton>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
