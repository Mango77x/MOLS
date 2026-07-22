import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import type { UnitEntity, WarehouseEntity } from '../../../api/entities'
import { useLookup } from '../../../api/lookups'
import { SecondaryButton, SelectField, SubmitButton, TextField } from '../../../components/form/fields'
import { enumLabel, ORDER_STATUS_LABELS } from '../../../lib/enumLabels'
import { positiveId, type WizardHeader } from './shared'

// PARTIALLY_SHIPPED deliberately excluded — that status is shipment-driven,
// never set manually at order creation/header-edit time.
const STATUS_OPTIONS = ['CREATED', 'VALIDATED', 'COMPLETED', 'CANCELLED'] as const

function buildSchema(t: TFunction) {
  return z.object({
    unitId: positiveId(t('orders.wizard.header.selectUnit')),
    warehouseId: positiveId(t('orders.wizard.header.selectWarehouse')),
    dateCreated: z.string().min(1, t('orders.wizard.header.dateRequired')),
    status: z.enum(STATUS_OPTIONS, { message: t('orders.wizard.header.selectStatus') }),
  })
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>

export default function HeaderStep({
  initial,
  onNext,
  onCancel,
}: {
  initial: WizardHeader
  onNext: (header: WizardHeader) => void
  onCancel: () => void
}) {
  const { t } = useTranslation()
  const { byId: units } = useLookup<UnitEntity>('/units')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(buildSchema(t)),
    defaultValues: initial as FormValues,
  })

  return (
    <form onSubmit={handleSubmit((values) => onNext(values))} className="space-y-4">
      <SelectField
        label={t('orders.unit')}
        id="unitId"
        defaultValue=""
        registration={register('unitId', { valueAsNumber: true })}
        error={errors.unitId?.message}
      >
        <option value="" disabled>
          {t('orders.wizard.header.selectUnit')}
        </option>
        {Object.values(units).map((u) => (
          <option key={u.id} value={u.id}>
            {u.name}
            {u.location ? ` — ${u.location}` : ''}
          </option>
        ))}
      </SelectField>
      <SelectField
        label={t('orders.originWarehouse')}
        id="warehouseId"
        defaultValue=""
        registration={register('warehouseId', { valueAsNumber: true })}
        error={errors.warehouseId?.message}
      >
        <option value="" disabled>
          {t('orders.wizard.header.selectWarehouse')}
        </option>
        {Object.values(warehouses).map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
            {w.location ? ` — ${w.location}` : ''}
          </option>
        ))}
      </SelectField>
      <p className="text-xs text-gray-500 dark:text-gray-400">{t('orders.wizard.header.warehouseHint')}</p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField
          label={t('orders.dateCreated')}
          id="dateCreated"
          type="date"
          registration={register('dateCreated')}
          error={errors.dateCreated?.message}
        />
        <SelectField
          label={t('common.status')}
          id="status"
          registration={register('status')}
          error={errors.status?.message}
        >
          <option value="" disabled>
            {t('orders.wizard.header.selectStatus')}
          </option>
          {STATUS_OPTIONS.map((value) => (
            <option key={value} value={value}>
              {enumLabel(ORDER_STATUS_LABELS, value)}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="flex gap-3">
        <SubmitButton submitting={false}>{t('orders.wizard.nextItems')}</SubmitButton>
        <SecondaryButton onClick={onCancel}>{t('common.cancel')}</SecondaryButton>
      </div>
    </form>
  )
}
