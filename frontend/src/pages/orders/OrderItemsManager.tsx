import { zodResolver } from '@hookform/resolvers/zod'
import { useCallback, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { api } from '../../api/client'
import type { OrderItemEntity, ResourceEntity } from '../../api/entities'
import { extractApiError } from '../../api/errors'
import { useLookup } from '../../api/lookups'
import type { PageResponse } from '../../components/table/useServerTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import { FormBanner, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { positiveId, positiveNumber } from '../../components/form/zodHelpers'

function buildSchema(t: TFunction) {
  return z.object({
    resourceId: positiveId(t('orders.wizard.items.selectResource')),
    quantity: positiveNumber(t('orders.itemsManager.quantityPositive')),
  })
}

type AddValues = z.infer<ReturnType<typeof buildSchema>>

/**
 * Inline items manager for an existing order — each change is persisted
 * immediately (no draft).
 *
 * `locked` hides the add form and the per-row Update/Remove actions,
 * leaving a read-only table — the API rejects any item change on a
 * COMPLETED/CANCELLED order anyway (see OrderItemService), so this mirrors
 * ShipmentFormPage's `itemsLocked` pattern instead of presenting live
 * controls that would only fail on submit.
 */
export default function OrderItemsManager({
  orderId,
  locked = false,
}: {
  orderId: number
  locked?: boolean
}) {
  const { t } = useTranslation()
  const { byId: resources } = useLookup<ResourceEntity>('/resources')
  const [items, setItems] = useState<OrderItemEntity[]>([])
  const [loading, setLoading] = useState(true)
  const [banner, setBanner] = useState<string | null>(null)
  const [pendingQuantities, setPendingQuantities] = useState<Record<number, number>>({})

  const loadItems = useCallback(async () => {
    setLoading(true)
    try {
      const response = await api.get<PageResponse<OrderItemEntity>>('/order-items', {
        params: { orderId, page: 0, size: 100 },
      })
      setItems(response.data.content)
    } finally {
      setLoading(false)
    }
  }, [orderId])

  useEffect(() => {
    void loadItems()
  }, [loadItems])

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddValues>({
    resolver: zodResolver(buildSchema(t)),
    defaultValues: { resourceId: undefined, quantity: undefined },
  })

  async function handleAdd(values: AddValues) {
    setBanner(null)
    try {
      await api.post('/order-items', { orderId, resourceId: values.resourceId, quantity: values.quantity })
      reset({ resourceId: undefined, quantity: undefined })
      await loadItems()
    } catch (error) {
      setBanner(extractApiError(error).message)
    }
  }

  async function handleUpdateQuantity(itemId: number) {
    const quantity = pendingQuantities[itemId]
    if (!quantity || quantity <= 0) return
    setBanner(null)
    try {
      await api.put(`/order-items/${itemId}`, { quantity })
      await loadItems()
    } catch (error) {
      setBanner(extractApiError(error).message)
    }
  }

  const [removingItemId, setRemovingItemId] = useState<number | null>(null)

  async function confirmRemove() {
    if (removingItemId === null) return
    const itemId = removingItemId
    setRemovingItemId(null)
    setBanner(null)
    try {
      await api.delete(`/order-items/${itemId}`)
      await loadItems()
    } catch (error) {
      setBanner(extractApiError(error).message)
    }
  }

  return (
    <div className="space-y-4">
      <FormBanner message={banner} />

      {locked && (
        <p className="text-xs text-gray-500 dark:text-gray-400">{t('orders.itemsManager.lockedHint')}</p>
      )}

      {!locked && (
        <form
          onSubmit={handleSubmit(handleAdd)}
          className="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_auto_auto] sm:items-end"
        >
          <SelectField
            label={t('common.resource')}
            id="addResourceId"
            defaultValue=""
            registration={register('resourceId', { valueAsNumber: true })}
            error={errors.resourceId?.message}
          >
            <option value="" disabled>
              {t('orders.wizard.items.selectResource')}
            </option>
            {Object.values(resources).map((r) => (
              <option key={r.id} value={r.id}>
                {r.name} ({r.type})
              </option>
            ))}
          </SelectField>
          <div className="w-32">
            <TextField
              label={t('common.quantity')}
              id="addQuantity"
              type="number"
              min={1}
              step={1}
              registration={register('quantity', { valueAsNumber: true })}
              error={errors.quantity?.message}
            />
          </div>
          <SubmitButton submitting={isSubmitting}>{t('orders.itemsManager.add')}</SubmitButton>
        </form>
      )}

      <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-800">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500 dark:bg-gray-800/50 dark:text-gray-400">
            <tr>
              <th className="px-3 py-2 font-medium">{t('common.resource')}</th>
              <th className="px-3 py-2 font-medium">{t('common.quantity')}</th>
              <th className="px-3 py-2 text-right font-medium">{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={3} className="px-3 py-4 text-center text-gray-400 dark:text-gray-500">
                  {t('orders.itemsManager.loading')}
                </td>
              </tr>
            )}
            {!loading && items.length === 0 && (
              <tr>
                <td colSpan={3} className="px-3 py-4 text-center text-gray-400 dark:text-gray-500">
                  {t('orders.itemsManager.noItems')}
                </td>
              </tr>
            )}
            {!loading &&
              items.map((item) => (
                <tr key={item.id} className="border-t border-gray-100 dark:border-gray-800">
                  <td className="px-3 py-2">
                    {resources[item.resourceId]?.name ?? `#${item.resourceId}`}
                    {resources[item.resourceId] && (
                      <span className="ml-1 text-xs text-gray-500 dark:text-gray-400">
                        ({resources[item.resourceId].type})
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-2">
                    {locked ? (
                      item.quantity
                    ) : (
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          aria-label={t('orders.itemsManager.quantityAriaLabel', {
                            name: resources[item.resourceId]?.name ?? `resource #${item.resourceId}`,
                          })}
                          min={1}
                          defaultValue={item.quantity}
                          onChange={(e) =>
                            setPendingQuantities((current) => ({ ...current, [item.id]: Number(e.target.value) }))
                          }
                          className="w-24 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
                        />
                        <button
                          type="button"
                          onClick={() => handleUpdateQuantity(item.id)}
                          className="text-army-700 underline dark:text-army-300"
                        >
                          {t('orders.itemsManager.update')}
                        </button>
                      </div>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right">
                    {!locked && (
                      <button
                        type="button"
                        onClick={() => setRemovingItemId(item.id)}
                        className="text-status-critical underline"
                      >
                        {t('orders.itemsManager.remove')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      <ConfirmDialog
        open={removingItemId !== null}
        title={t('orders.itemsManager.removeTitle')}
        message={t('orders.itemsManager.removeMessage')}
        confirmLabel={t('orders.itemsManager.remove')}
        onConfirm={confirmRemove}
        onCancel={() => setRemovingItemId(null)}
      />
    </div>
  )
}
