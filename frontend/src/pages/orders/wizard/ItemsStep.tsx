import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api } from '../../../api/client'
import type { ResourceEntity } from '../../../api/entities'
import { useLookup } from '../../../api/lookups'
import type { PageResponse } from '../../../components/table/useServerTable'
import { SecondaryButton, SelectField, SubmitButton, TextField } from '../../../components/form/fields'
import { positiveId, positiveNumber } from '../../../components/form/zodHelpers'
import type { DraftItem } from './shared'

const schema = z.object({
  resourceId: positiveId('Select a resource'),
  quantity: positiveNumber('Order item quantity must be greater than 0'),
})

type AddItemValues = z.infer<typeof schema>

interface StockRecord {
  resourceId: number
  quantity: number
  reservedQuantity: number
}

/**
 * Best-effort client-side check: physical stock minus what's already
 * reserved, for this resource in the order's warehouse specifically — the
 * same (resource, warehouse) pair the server will actually check against
 * (see OrderItemService.reserve). At most one row can match, since stocks
 * are unique per (resource, warehouse).
 */
async function fetchTrulyAvailableQuantity(resourceId: number, warehouseId: number): Promise<number> {
  const response = await api.get<PageResponse<StockRecord>>('/stocks', {
    params: { resourceId, warehouseId, page: 0, size: 1 },
  })
  const stock = response.data.content[0]
  return stock ? stock.quantity - stock.reservedQuantity : 0
}

export default function ItemsStep({
  warehouseId,
  items,
  onChange,
  onNext,
  onBack,
}: {
  warehouseId: number
  items: DraftItem[]
  onChange: (items: DraftItem[]) => void
  onNext: () => void
  onBack: () => void
}) {
  const { byId: resources } = useLookup<ResourceEntity>('/resources')
  const [availabilityNote, setAvailabilityNote] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AddItemValues>({
    resolver: zodResolver(schema),
    defaultValues: { resourceId: undefined, quantity: undefined },
  })

  async function handleAdd(values: AddItemValues) {
    setAvailabilityNote(null)
    setChecking(true)
    try {
      const available = await fetchTrulyAvailableQuantity(values.resourceId, warehouseId)
      const resource = resources[values.resourceId]
      if (available < values.quantity) {
        setAvailabilityNote(
          `Warning: only ${available} unit(s) of ${resource?.name ?? `resource #${values.resourceId}`} are still unreserved in this order's warehouse. You can still add this item, but creating the order may fail.`,
        )
      }
      onChange([
        ...items,
        { resourceId: values.resourceId, resourceName: resource?.name ?? `#${values.resourceId}`, quantity: values.quantity },
      ])
      reset({ resourceId: undefined, quantity: undefined })
    } finally {
      setChecking(false)
    }
  }

  function handleRemove(index: number) {
    onChange(items.filter((_, i) => i !== index))
  }

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit(handleAdd)} className="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_auto_auto] sm:items-end">
        <SelectField
          label="Resource"
          id="itemResourceId"
          defaultValue=""
          registration={register('resourceId', { valueAsNumber: true })}
          error={errors.resourceId?.message}
        >
          <option value="" disabled>
            Select a resource
          </option>
          {Object.values(resources).map((r) => (
            <option key={r.id} value={r.id}>
              {r.name} ({r.type})
            </option>
          ))}
        </SelectField>
        <div className="w-32">
          <TextField
            label="Quantity"
            id="itemQuantity"
            type="number"
            min={1}
            step={1}
            registration={register('quantity', { valueAsNumber: true })}
            error={errors.quantity?.message}
          />
        </div>
        <SubmitButton submitting={checking}>{checking ? 'Checking…' : 'Add'}</SubmitButton>
      </form>

      {availabilityNote && (
        <p role="alert" className="rounded-lg border border-status-warn/30 bg-status-warn/10 px-3 py-2 text-sm text-status-warn">
          {availabilityNote}
        </p>
      )}

      <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-800">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500 dark:bg-gray-800/50 dark:text-gray-400">
            <tr>
              <th className="px-3 py-2 font-medium">Resource</th>
              <th className="px-3 py-2 font-medium">Quantity</th>
              <th className="px-3 py-2 text-right font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 && (
              <tr>
                <td colSpan={3} className="px-3 py-4 text-center text-gray-400 dark:text-gray-500">
                  No items added yet.
                </td>
              </tr>
            )}
            {items.map((item, index) => (
              <tr key={`${item.resourceId}-${index}`} className="border-t border-gray-100 dark:border-gray-800">
                <td className="px-3 py-2">{item.resourceName}</td>
                <td className="px-3 py-2">{item.quantity}</td>
                <td className="px-3 py-2 text-right">
                  <button
                    type="button"
                    onClick={() => handleRemove(index)}
                    className="text-status-critical underline"
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex gap-3">
        <button
          type="button"
          disabled={items.length === 0}
          onClick={onNext}
          className="rounded-lg bg-army-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-army-600 disabled:opacity-60"
        >
          Next: Shipment
        </button>
        <SecondaryButton onClick={onBack}>Back</SecondaryButton>
      </div>
      {items.length === 0 && (
        <p className="text-xs text-gray-500 dark:text-gray-400">Add at least one item to continue.</p>
      )}
    </div>
  )
}
