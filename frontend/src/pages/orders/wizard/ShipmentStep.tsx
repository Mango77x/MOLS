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
    status: z.enum(['PLANNED', 'IN_TRANSIT', 'DELIVERED']),
  })
  .superRefine((values, ctx) => {
    if (!values.enabled) return
    if (!values.vehicleId) {
      ctx.addIssue({ code: 'custom', message: 'Select a vehicle', path: ['vehicleId'] })
    }
  })

export default function ShipmentStep({
  warehouseId,
  initial,
  submitting,
  onSubmit,
  onBack,
}: {
  warehouseId: number
  initial: WizardShipment
  submitting: boolean
  onSubmit: (shipment: WizardShipment) => void
  onBack: () => void
}) {
  const { byId: vehicles } = useLookup<VehicleEntity>('/vehicles')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')
  const warehouse = warehouses[warehouseId]

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
          <div>
            <span className="block text-sm font-medium text-gray-700 dark:text-gray-300">Origin warehouse</span>
            <p className="mt-1 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-600 dark:border-gray-800 dark:bg-gray-800/50 dark:text-gray-300">
              {warehouse ? `${warehouse.name}${warehouse.location ? ` — ${warehouse.location}` : ''}` : '…'}
            </p>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Fixed to the order's warehouse.</p>
          </div>
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
