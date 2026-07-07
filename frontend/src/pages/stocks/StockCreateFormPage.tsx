import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { ResourceEntity, WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useLookup } from '../../api/lookups'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { nonNegativeNumber, positiveId } from '../../components/form/zodHelpers'

const schema = z.object({
  resourceId: positiveId('Select a resource'),
  warehouseId: positiveId('Select a warehouse'),
  quantity: nonNegativeNumber('Stock quantity must be zero or greater'),
})

type FormValues = z.infer<typeof schema>

export default function StockCreateFormPage() {
  const navigate = useNavigate()
  const { byId: resources } = useLookup<ResourceEntity>('/resources')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { resourceId: undefined, warehouseId: undefined, quantity: 0 },
  })

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      await api.post('/stocks', values)
      navigate('/stocks')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  return (
    <FormPage title="New stock record" backTo="/stocks">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <SelectField
          label="Resource"
          id="resourceId"
          defaultValue=""
          registration={register('resourceId', { valueAsNumber: true })}
          error={errors.resourceId?.message}
        >
          <option value="" disabled>
            Select a resource
          </option>
          {Object.values(resources).map((r) => (
            <option key={r.id} value={r.id}>
              {r.name} ({r.type}, {r.criticality})
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Warehouse"
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
        <TextField
          label="Initial quantity"
          id="quantity"
          type="number"
          min={0}
          step={1}
          registration={register('quantity', { valueAsNumber: true })}
          error={errors.quantity?.message}
        />
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? 'Creating…' : 'Create'}</SubmitButton>
          <SecondaryButton onClick={() => navigate('/stocks')}>Cancel</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
