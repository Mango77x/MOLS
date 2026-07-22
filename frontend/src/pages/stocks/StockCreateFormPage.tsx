import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { ResourceEntity, WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useLookup } from '../../api/lookups'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { useToast } from '../../components/toast/toastContext'
import { nonNegativeNumber, positiveId } from '../../components/form/zodHelpers'

function buildSchema(t: TFunction) {
  return z.object({
    resourceId: positiveId(t('stocks.create.selectResource')),
    warehouseId: positiveId(t('stocks.create.selectWarehouse')),
    quantity: nonNegativeNumber(t('stocks.create.quantityNonNegative')),
  })
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>

export default function StockCreateFormPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { byId: resources } = useLookup<ResourceEntity>('/resources')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(buildSchema(t)),
    defaultValues: { resourceId: undefined, warehouseId: undefined, quantity: 0 },
  })

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      await api.post('/stocks', values)
      showToast(t('stocks.created'), 'success')
      navigate('/stocks')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  return (
    <FormPage title={t('stocks.newStock')} backTo="/stocks">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <SelectField
          label={t('common.resource')}
          id="resourceId"
          defaultValue=""
          registration={register('resourceId', { valueAsNumber: true })}
          error={errors.resourceId?.message}
        >
          <option value="" disabled>
            {t('stocks.create.selectResource')}
          </option>
          {Object.values(resources).map((r) => (
            <option key={r.id} value={r.id}>
              {r.name} ({r.type}, {r.criticality})
            </option>
          ))}
        </SelectField>
        <SelectField
          label={t('common.warehouse')}
          id="warehouseId"
          defaultValue=""
          registration={register('warehouseId', { valueAsNumber: true })}
          error={errors.warehouseId?.message}
        >
          <option value="" disabled>
            {t('stocks.create.selectWarehouse')}
          </option>
          {Object.values(warehouses).map((w) => (
            <option key={w.id} value={w.id}>
              {w.name} — {w.location}
            </option>
          ))}
        </SelectField>
        <TextField
          label={t('stocks.create.initialQuantity')}
          id="quantity"
          type="number"
          min={0}
          step={1}
          registration={register('quantity', { valueAsNumber: true })}
          error={errors.quantity?.message}
        />
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>
            {isSubmitting ? t('stocks.create.creating') : t('stocks.create.createAction')}
          </SubmitButton>
          <SecondaryButton onClick={() => navigate('/stocks')}>{t('common.cancel')}</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
