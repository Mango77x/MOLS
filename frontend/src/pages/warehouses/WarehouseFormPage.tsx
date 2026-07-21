import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { coordinate } from '../../components/form/zodHelpers'
import LocationPicker from '../../components/map/LocationPicker'
import { useToast } from '../../components/toast/toastContext'

const schema = z.object({
  name: z
    .string()
    .min(2, 'Warehouse name must be between 2 and 100 characters')
    .max(100, 'Warehouse name must be between 2 and 100 characters'),
  location: z
    .string()
    .min(2, 'Warehouse location must be between 2 and 200 characters')
    .max(200, 'Warehouse location must be between 2 and 200 characters'),
  latitude: coordinate(-90, 90, 'Latitude'),
  longitude: coordinate(-180, 180, 'Longitude'),
})

type FormValues = z.infer<typeof schema>

export default function WarehouseFormPage() {
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { data: warehouse, loading, notFound } = useEntity<WarehouseEntity>(
    isEdit ? `/warehouses/${id}` : null,
  )
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', location: '', latitude: undefined, longitude: undefined },
  })

  useEffect(() => {
    if (warehouse) {
      reset({
        name: warehouse.name,
        location: warehouse.location ?? '',
        latitude: warehouse.latitude ?? undefined,
        longitude: warehouse.longitude ?? undefined,
      })
    }
  }, [warehouse, reset])

  const latitude = watch('latitude')
  const longitude = watch('longitude')

  async function onSubmit(values: FormValues) {
    setBanner(null)
    const payload = {
      name: values.name,
      location: values.location,
      latitude: values.latitude ?? null,
      longitude: values.longitude ?? null,
    }
    try {
      if (isEdit) {
        await api.put(`/warehouses/${id}`, payload)
        showToast('Warehouse updated.', 'success')
      } else {
        await api.post('/warehouses', payload)
        showToast('Warehouse created.', 'success')
      }
      navigate('/warehouses')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (isEdit && loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading warehouse…</p>
  }
  if (isEdit && notFound) {
    return <FormBanner message="Warehouse not found." />
  }

  return (
    <FormPage title={isEdit ? 'Edit warehouse' : 'New warehouse'} backTo="/warehouses" wide>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <TextField
          label="Name"
          id="name"
          registration={register('name')}
          error={errors.name?.message}
        />
        <TextField
          label="Location"
          id="location"
          registration={register('location')}
          error={errors.location?.message}
        />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField
            label="Latitude (optional)"
            id="latitude"
            type="number"
            step="any"
            min={-90}
            max={90}
            placeholder="e.g. 40.4168"
            registration={register('latitude', { valueAsNumber: true })}
            error={errors.latitude?.message}
          />
          <TextField
            label="Longitude (optional)"
            id="longitude"
            type="number"
            step="any"
            min={-180}
            max={180}
            placeholder="e.g. -3.7038"
            registration={register('longitude', { valueAsNumber: true })}
            error={errors.longitude?.message}
          />
        </div>
        <div>
          <p className="mb-1 text-sm font-medium">Pick on map (optional)</p>
          <LocationPicker
            latitude={latitude ?? null}
            longitude={longitude ?? null}
            onChange={(lat, lng) => {
              setValue('latitude', Number(lat.toFixed(6)), { shouldValidate: true })
              setValue('longitude', Number(lng.toFixed(6)), { shouldValidate: true })
            }}
          />
        </div>
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save'}</SubmitButton>
          <SecondaryButton onClick={() => navigate('/warehouses')}>Cancel</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
