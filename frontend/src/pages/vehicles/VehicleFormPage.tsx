import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { VehicleEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { positiveNumber } from '../../components/form/zodHelpers'
import { useToast } from '../../components/toast/toastContext'
import { enumLabel, VEHICLE_STATUS_LABELS, VEHICLE_TYPE_LABELS } from '../../lib/enumLabels'

const schema = z.object({
  type: z.enum(['LAND', 'SEA', 'AIR'], { message: 'Select a type' }),
  capacity: positiveNumber('Vehicle capacity must be greater than 0'),
  status: z.enum(['AVAILABLE', 'IN_USE', 'IN_REPAIR'], { message: 'Select a status' }),
})

type FormValues = z.infer<typeof schema>

export default function VehicleFormPage() {
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { data: vehicle, loading, notFound } = useEntity<VehicleEntity>(
    isEdit ? `/vehicles/${id}` : null,
  )
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { type: undefined, capacity: undefined, status: undefined },
  })

  useEffect(() => {
    if (vehicle) {
      reset({
        type: vehicle.type as FormValues['type'],
        capacity: vehicle.capacity,
        status: vehicle.status,
      })
    }
  }, [vehicle, reset])

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      if (isEdit) {
        await api.put(`/vehicles/${id}`, values)
        showToast('Vehicle updated.', 'success')
      } else {
        await api.post('/vehicles', values)
        showToast('Vehicle created.', 'success')
      }
      navigate('/vehicles')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (isEdit && loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading vehicle…</p>
  }
  if (isEdit && notFound) {
    return <FormBanner message="Vehicle not found." />
  }

  return (
    <FormPage title={isEdit ? 'Edit vehicle' : 'New vehicle'} backTo="/vehicles">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <SelectField
          label="Type"
          id="type"
          registration={register('type')}
          error={errors.type?.message}
          defaultValue=""
        >
          <option value="" disabled>
            Select a type
          </option>
          {Object.entries(VEHICLE_TYPE_LABELS).map(([value]) => (
            <option key={value} value={value}>
              {enumLabel(VEHICLE_TYPE_LABELS, value)}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Capacity (kg)"
          id="capacity"
          type="number"
          min={1}
          step={1}
          registration={register('capacity', { valueAsNumber: true })}
          error={errors.capacity?.message}
        />
        <SelectField
          label="Status"
          id="status"
          registration={register('status')}
          error={errors.status?.message}
          defaultValue=""
        >
          <option value="" disabled>
            Select a status
          </option>
          {Object.entries(VEHICLE_STATUS_LABELS).map(([value]) => (
            <option key={value} value={value}>
              {enumLabel(VEHICLE_STATUS_LABELS, value)}
            </option>
          ))}
        </SelectField>
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save'}</SubmitButton>
          <SecondaryButton onClick={() => navigate('/vehicles')}>Cancel</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
