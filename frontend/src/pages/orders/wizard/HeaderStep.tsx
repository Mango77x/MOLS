import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import type { UnitEntity, WarehouseEntity } from '../../../api/entities'
import { useLookup } from '../../../api/lookups'
import { SecondaryButton, SelectField, SubmitButton, TextField } from '../../../components/form/fields'
import { enumLabel, ORDER_STATUS_LABELS } from '../../../lib/enumLabels'
import { positiveId, type WizardHeader } from './shared'

// PARTIALLY_SHIPPED deliberately excluded — that status is shipment-driven,
// never set manually at order creation/header-edit time.
const STATUS_OPTIONS = ['CREATED', 'VALIDATED', 'COMPLETED', 'CANCELLED'] as const

const schema = z.object({
  unitId: positiveId('Select a unit'),
  warehouseId: positiveId('Select an origin warehouse'),
  dateCreated: z.string().min(1, 'Order creation date is required'),
  status: z.enum(STATUS_OPTIONS, { message: 'Select a status' }),
})

type FormValues = z.infer<typeof schema>

export default function HeaderStep({
  initial,
  onNext,
  onCancel,
}: {
  initial: WizardHeader
  onNext: (header: WizardHeader) => void
  onCancel: () => void
}) {
  const { byId: units } = useLookup<UnitEntity>('/units')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: initial as FormValues,
  })

  return (
    <form onSubmit={handleSubmit((values) => onNext(values))} className="space-y-4">
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
      <SelectField
        label="Origin warehouse"
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
            {w.name}
            {w.location ? ` — ${w.location}` : ''}
          </option>
        ))}
      </SelectField>
      <p className="text-xs text-gray-500 dark:text-gray-400">
        Fixed for the whole order — every item reserves stock from this warehouse, and the shipment inherits it
        automatically.
      </p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField
          label="Date created"
          id="dateCreated"
          type="date"
          registration={register('dateCreated')}
          error={errors.dateCreated?.message}
        />
        <SelectField label="Status" id="status" registration={register('status')} error={errors.status?.message}>
          <option value="" disabled>
            Select a status
          </option>
          {STATUS_OPTIONS.map((value) => (
            <option key={value} value={value}>
              {enumLabel(ORDER_STATUS_LABELS, value)}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="flex gap-3">
        <SubmitButton submitting={false}>Next: Items</SubmitButton>
        <SecondaryButton onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  )
}
