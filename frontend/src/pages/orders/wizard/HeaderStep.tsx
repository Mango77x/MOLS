import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import type { UnitEntity } from '../../../api/entities'
import { useLookup } from '../../../api/lookups'
import { SecondaryButton, SelectField, SubmitButton, TextField } from '../../../components/form/fields'
import { positiveId, type WizardHeader } from './shared'

const schema = z.object({
  unitId: positiveId('Select a unit'),
  dateCreated: z.string().min(1, 'Order creation date is required'),
  status: z.enum(['CREATED', 'VALIDATED', 'COMPLETED', 'CANCELLED'], { message: 'Select a status' }),
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
          <option value="CREATED">Created</option>
          <option value="VALIDATED">Validated</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </SelectField>
      </div>
      <div className="flex gap-3">
        <SubmitButton submitting={false}>Next: Items</SubmitButton>
        <SecondaryButton onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  )
}
