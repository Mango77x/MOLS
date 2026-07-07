import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import type { VehicleEntity, WarehouseEntity } from '../../../api/entities'
import { useLookup } from '../../../api/lookups'
import { SecondaryButton, SelectField, SubmitButton } from '../../../components/form/fields'
import { positiveId, type WizardShipment } from './shared'

const schema = z
  .object({
    enabled: z.boolean(),
    vehicleId: positiveId('Select a vehicle').optional(),
    warehouseId: positiveId('Select a warehouse').optional(),
    status: z.enum(['PLANNED', 'IN_TRANSIT', 'DELIVERED']),
  })
  .superRefine((values, ctx) => {
    if (!values.enabled) return
    if (!values.vehicleId) {
      ctx.addIssue({ code: 'custom', message: 'Select a vehicle', path: ['vehicleId'] })
    }
    if (!values.warehouseId) {
      ctx.addIssue({ code: 'custom', message: 'Select a warehouse', path: ['warehouseId'] })
    }
  })

export default function ShipmentStep({
  initial,
  submitting,
  onSubmit,
  onBack,
}: {
  initial: WizardShipment
  submitting: boolean
  onSubmit: (shipment: WizardShipment) => void
  onBack: () => void
}) {
  const { byId: vehicles } = useLookup<VehicleEntity>('/vehicles')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<WizardShipment>({
    resolver: zodResolver(schema),
    defaultValues: initial,
  })

  const enabled = watch('enabled')

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
      <label className="flex items-center gap-2 text-sm font-medium">
        <input type="checkbox" {...register('enabled')} />
        Create a shipment for this order now
      </label>

      {enabled && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <SelectField
            label="Vehicle"
            id="vehicleId"
            defaultValue=""
            registration={register('vehicleId', { valueAsNumber: true })}
            error={errors.vehicleId?.message}
          >
            <option value="" disabled>
              Select a vehicle
            </option>
            {Object.values(vehicles).map((v) => (
              <option key={v.id} value={v.id}>
                {v.type} — {v.status}
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
                {w.name} — {w.location}
              </option>
            ))}
          </SelectField>
          <SelectField label="Status" id="shipmentStatus" registration={register('status')} error={undefined}>
            <option value="PLANNED">Planned</option>
            <option value="IN_TRANSIT">In transit</option>
            <option value="DELIVERED">Delivered</option>
          </SelectField>
        </div>
      )}

      <div className="flex gap-3">
        <SubmitButton submitting={submitting}>{submitting ? 'Creating…' : 'Create order'}</SubmitButton>
        <SecondaryButton onClick={onBack}>Back</SecondaryButton>
      </div>
    </form>
  )
}
