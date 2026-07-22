import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { useDuplicateNameWarning } from '../../components/form/useDuplicateNameWarning'
import { coordinate } from '../../components/form/zodHelpers'
import LocationPicker from '../../components/map/LocationPicker'
import { useToast } from '../../components/toast/toastContext'

// A plain function (not a hook) so it can be called from useForm's resolver
// setup and used for type inference (`ReturnType<typeof buildSchema>`) alike.
// Has to be a function at all, rather than a module-level constant, because
// its validation messages need the active i18next language at the time the
// form renders, not whatever was active when the module first loaded.
function buildSchema(t: TFunction) {
  return z.object({
    name: z
      .string()
      .min(2, t('warehouses.form.nameLength'))
      .max(100, t('warehouses.form.nameLength')),
    location: z
      .string()
      .min(2, t('warehouses.form.locationLength'))
      .max(200, t('warehouses.form.locationLength')),
    latitude: coordinate(-90, 90, t('common.latitude')),
    longitude: coordinate(-180, 180, t('common.longitude')),
  })
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>

export default function WarehouseFormPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { data: warehouse, loading, notFound } = useEntity<WarehouseEntity>(
    isEdit ? `/warehouses/${id}` : null,
  )
  const [banner, setBanner] = useState<string | null>(null)
  const { warning: nameWarning, checkName } = useDuplicateNameWarning(
    '/warehouses',
    t('warehouses.entityName'),
    isEdit ? Number(id) : undefined,
  )

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(buildSchema(t)),
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
        showToast(t('warehouses.updated'), 'success')
      } else {
        await api.post('/warehouses', payload)
        showToast(t('warehouses.created'), 'success')
      }
      navigate('/warehouses')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (isEdit && loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">{t('warehouses.loading')}</p>
  }
  if (isEdit && notFound) {
    return <FormBanner message={t('warehouses.notFound')} />
  }

  return (
    <FormPage
      title={isEdit ? t('warehouses.editWarehouse') : t('warehouses.newWarehouse')}
      backTo="/warehouses"
      wide
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <TextField
          label={t('common.name')}
          id="name"
          registration={register('name', { onBlur: (e) => checkName(e.target.value) })}
          error={errors.name?.message}
          warning={nameWarning}
        />
        <TextField
          label={t('common.location')}
          id="location"
          registration={register('location')}
          error={errors.location?.message}
        />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField
            label={t('common.latitudeOptional')}
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
            label={t('common.longitudeOptional')}
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
          <p className="mb-1 text-sm font-medium">{t('common.pickOnMap')}</p>
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
          <SubmitButton submitting={isSubmitting}>
            {isSubmitting ? t('common.saving') : t('common.save')}
          </SubmitButton>
          <SecondaryButton onClick={() => navigate('/warehouses')}>{t('common.cancel')}</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
