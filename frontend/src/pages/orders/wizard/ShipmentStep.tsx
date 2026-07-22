import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import type { VehicleEntity, WarehouseEntity } from '../../../api/entities'
import { useLookup } from '../../../api/lookups'
import { SecondaryButton, SelectField, SubmitButton } from '../../../components/form/fields'
import { enumLabel, SHIPMENT_STATUS_LABELS, VEHICLE_STATUS_LABELS, VEHICLE_TYPE_LABELS } from '../../../lib/enumLabels'
import { positiveId, type DraftItem, type WizardShipment } from './shared'

function buildSchema(t: TFunction) {
  return z
    .object({
      enabled: z.boolean(),
      vehicleId: positiveId(t('orders.wizard.shipment.selectVehicle')).optional(),
      status: z.enum(['PLANNED', 'IN_TRANSIT', 'DELIVERED']),
      items: z.array(z.object({ resourceId: z.number(), quantity: z.number() })),
    })
    .superRefine((values, ctx) => {
      if (!values.enabled) return
      if (!values.vehicleId) {
        ctx.addIssue({
          code: 'custom',
          message: t('orders.wizard.shipment.selectVehicleRequired'),
          path: ['vehicleId'],
        })
      }
    })
}

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
  const { t } = useTranslation()
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
    resolver: zodResolver(buildSchema(t)),
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
        {t('orders.wizard.shipment.createNow')}
      </label>

      {enabled && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <SelectField
              label={t('orders.wizard.shipment.vehicle')}
              id="vehicleId"
              defaultValue=""
              registration={register('vehicleId', { valueAsNumber: true })}
              error={errors.vehicleId?.message}
            >
              <option value="" disabled>
                {t('orders.wizard.shipment.selectVehicle')}
              </option>
              {Object.values(vehicles).map((v) => (
                <option key={v.id} value={v.id}>
                  {t('orders.wizard.shipment.vehicleOption', {
                    id: v.id,
                    type: enumLabel(VEHICLE_TYPE_LABELS, v.type),
                    status: enumLabel(VEHICLE_STATUS_LABELS, v.status),
                  })}
                </option>
              ))}
            </SelectField>
            <div>
              <span className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                {t('orders.originWarehouse')}
              </span>
              <p className="mt-1 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-600 dark:border-gray-800 dark:bg-gray-800/50 dark:text-gray-300">
                {warehouse ? `${warehouse.name}${warehouse.location ? ` — ${warehouse.location}` : ''}` : '…'}
              </p>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {t('orders.wizard.shipment.warehouseFixedHint')}
              </p>
            </div>
            <SelectField label={t('common.status')} id="shipmentStatus" registration={register('status')} error={undefined}>
              {Object.entries(SHIPMENT_STATUS_LABELS).map(([value]) => (
                <option key={value} value={value}>
                  {enumLabel(SHIPMENT_STATUS_LABELS, value)}
                </option>
              ))}
            </SelectField>
          </div>

          <div>
            <span className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              {t('orders.wizard.shipment.itemsToShip')}
            </span>
            <div className="mt-1 overflow-hidden rounded-lg border border-gray-200 dark:border-gray-800">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500 dark:bg-gray-800/50 dark:text-gray-400">
                  <tr>
                    <th className="px-3 py-2 font-medium">{t('common.resource')}</th>
                    <th className="px-3 py-2 font-medium">{t('orders.wizard.shipment.ordered')}</th>
                    <th className="px-3 py-2 font-medium">{t('orders.wizard.shipment.shipNow')}</th>
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
                          aria-label={t('orders.wizard.shipment.shipNowAriaLabel', { name: item.resourceName })}
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
              {t('orders.wizard.shipment.shipNowHint')}
            </p>
          </div>
        </div>
      )}

      <div className="flex gap-3">
        <SubmitButton submitting={submitting}>
          {submitting ? t('orders.wizard.creating') : t('orders.wizard.createOrder')}
        </SubmitButton>
        <SecondaryButton onClick={onBack}>{t('common.back')}</SecondaryButton>
      </div>
    </form>
  )
}
