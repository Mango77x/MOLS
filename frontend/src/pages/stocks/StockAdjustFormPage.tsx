import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { ResourceEntity, StockEntity, WarehouseEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { useToast } from '../../components/toast/toastContext'
import { positiveNumber } from '../../components/form/zodHelpers'

function buildSchema(t: TFunction) {
  return z.object({
    operation: z.enum(['INCREASE', 'DECREASE'], { message: t('stocks.adjustPage.selectAction') }),
    amount: positiveNumber(t('stocks.adjustPage.amountPositive')),
  })
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>

export default function StockAdjustFormPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const { showToast } = useToast()
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
    resolver: zodResolver(buildSchema(t)),
    defaultValues: { operation: undefined, amount: undefined },
  })

  async function onSubmit(values: FormValues) {
    setBanner(null)
    const delta = values.operation === 'INCREASE' ? values.amount : -values.amount
    try {
      await api.patch(`/stocks/${id}/adjust`, { delta })
      showToast(t('stocks.adjusted'), 'success')
      navigate('/stocks')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">{t('stocks.adjustPage.loading')}</p>
  }
  if (notFound || !stock) {
    return <FormBanner message={t('stocks.adjustPage.notFound')} />
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">{t('stocks.adjustPage.title')}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {t('stocks.adjustPage.stockId', { id: stock.id })}
          </p>
        </div>
        <Link to="/stocks" className="text-sm font-medium text-army-700 underline dark:text-army-300">
          {t('stocks.adjustPage.back')}
        </Link>
      </div>

      <div className="flex flex-col gap-4 lg:flex-row">
        <div className="rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900 lg:w-80">
          <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">
            {t('stocks.adjustPage.current')}
          </p>
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
            <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">
              {t('common.quantity')}
            </p>
            <div className="text-2xl font-semibold">{stock.quantity}</div>
          </div>
        </div>

        <div className="max-w-xl flex-1 rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <FormBanner message={banner} />
            <SelectField
              label={t('stocks.adjustPage.action')}
              id="operation"
              registration={register('operation')}
              error={errors.operation?.message}
              defaultValue=""
            >
              <option value="" disabled>
                {t('stocks.adjustPage.selectAction')}
              </option>
              <option value="INCREASE">{t('stocks.adjustPage.increase')}</option>
              <option value="DECREASE">{t('stocks.adjustPage.decrease')}</option>
            </SelectField>
            <TextField
              label={t('stocks.adjustPage.amount')}
              id="amount"
              type="number"
              min={1}
              step={1}
              placeholder="e.g. 5"
              registration={register('amount', { valueAsNumber: true })}
              error={errors.amount?.message}
            />
            <p className="text-xs text-gray-500 dark:text-gray-400">{t('stocks.adjustPage.hint')}</p>
            <div className="flex gap-3">
              <SubmitButton submitting={isSubmitting}>
                {isSubmitting ? t('stocks.adjustPage.applying') : t('stocks.adjustPage.applyChange')}
              </SubmitButton>
              <SecondaryButton onClick={() => navigate('/stocks')}>{t('common.cancel')}</SecondaryButton>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
