import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import type { VehicleEntity, WarehouseEntity } from '../../../api/entities'
import { useLookup } from '../../../api/lookups'
import { SecondaryButton, SelectField, SubmitButton } from '../../../components/form/fields'
import { enumLabel, SHIPMENT_STATUS_LABELS, VEHICLE_STATUS_LABELS, VEHICLE_TYPE_LABELS } from '../../../lib/enumLabels'
import { positiveId, type DraftItem, type WizardShipment } from './shared'

const schema = z
  .object({
    enabled: z.boolean(),
    vehicleId: positiveId('Select a vehicle').optional(),
    status: z.enum(['PLANNED', 'IN_TRANSIT', 'DELIVERED']),
    items: z.array(z.object({ resourceId: z.number(), quantity: z.number() })),
  })
  .superRefine((values, ctx) => {
    if (!values.enabled) return
    if (!values.vehicleId) {
      ctx.addIssue({ code: 'custom', message: 'Select a vehicle', path: ['vehicleId'] })
    }
  })

export default function ShipmentStep({
  warehouseId,
  draftItems,
  initial,
  submitting,
  onSubmit,
  onBack,
}: {
  warehouseId: number
  draftItems: DraftItem[]
  initial: WizardShipment
  submitting: boolean
  onSubmit: (shipment: WizardShipment) => void
  onBack: () => void
}) {
  const { byId: vehicles } = useLookup<VehicleEntity>('/vehicles')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')
  const warehouse = warehouses[warehouseId]

  // Ship-now quantity per draft item, keyed by resourceId — defaults to the
  // full ordered quantity (the common case: one shipment covers everything).
  const [itemQuantities, setItemQuantities] = useState<Record<number, number>>(() =>
    Object.fromEntries(draftItems.map((item) => [item.resourceId, item.quantity])),
  )

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

  function submit(values: WizardShipment) {
    const items = draftItems
      .map((item) => ({ resourceId: item.resourceId, quantity: itemQuantities[item.resourceId] ?? 0 }))
      .filter((line) => line.quantity > 0)
    onSubmit({ ...values, items })
  }

  return (
    <form onSubmit={handleSubmit(submit)} className="space-y-4">
      <label className="flex items-center gap-2 text-sm font-medium">
        <input type="checkbox" {...register('enabled')} />
        Create a shipment for this order now
      </label>

      {enabled && (
        <div className="space-y-4">
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
                  Vehicle #{v.id} — {enumLabel(VEHICLE_TYPE_LABELS, v.type)} — {enumLabel(VEHICLE_STATUS_LABELS, v.status)}
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
              {Object.entries(SHIPMENT_STATUS_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </SelectField>
          </div>

          <div>
            <span className="block text-sm font-medium text-gray-700 dark:text-gray-300">Items to ship</span>
            <div className="mt-1 overflow-hidden rounded-lg border border-gray-200 dark:border-gray-800">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500 dark:bg-gray-800/50 dark:text-gray-400">
                  <tr>
                    <th className="px-3 py-2 font-medium">Resource</th>
                    <th className="px-3 py-2 font-medium">Ordered</th>
                    <th className="px-3 py-2 font-medium">Ship now</th>
                  </tr>
                </thead>
                <tbody>
                  {draftItems.map((item) => (
                    <tr key={item.resourceId} className="border-t border-gray-100 dark:border-gray-800">
                      <td className="px-3 py-2">{item.resourceName}</td>
                      <td className="px-3 py-2">{item.quantity}</td>
                      <td className="px-3 py-2">
                        <input
                          type="number"
                          aria-label={`Quantity to ship for ${item.resourceName}`}
                          min={0}
                          max={item.quantity}
                          value={itemQuantities[item.resourceId] ?? 0}
                          onChange={(e) =>
                            setItemQuantities((current) => ({
                              ...current,
                              [item.resourceId]: Number(e.target.value),
                            }))
                          }
                          className="w-24 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
                        />
                        <span className="ml-1 text-xs text-gray-500 dark:text-gray-400">/ {item.quantity}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              Defaults to shipping everything now; lower a quantity to leave the rest for a later shipment.
            </p>
          </div>
        </div>
      )}

      <div className="flex gap-3">
        <SubmitButton submitting={submitting}>{submitting ? 'Creating…' : 'Create order'}</SubmitButton>
        <SecondaryButton onClick={onBack}>Back</SecondaryButton>
      </div>
    </form>
  )
}
