import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import type { PageResponse } from '../../components/table/useServerTable'
import type { OrderItemEntity, ResourceEntity } from '../../api/entities'

/** Expanded sub-row: fetches and lists the order's line items on demand. */
export default function OrderItemsRow({
  orderId,
  colSpan,
  resources,
}: {
  orderId: number
  colSpan: number
  resources: Record<number, ResourceEntity>
}) {
  const [items, setItems] = useState<OrderItemEntity[] | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    api
      // The `orderId` filter switches the response to the PageResponse
      // envelope (same as every other list endpoint); size 100 comfortably
      // covers a single order's line items in one page.
      .get<PageResponse<OrderItemEntity>>('/order-items', { params: { orderId, page: 0, size: 100 } })
      .then((response) => {
        if (!cancelled) setItems(response.data.content)
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })
    return () => {
      cancelled = true
    }
  }, [orderId])

  return (
    <tr className="bg-gray-50 dark:bg-gray-800/50">
      <td colSpan={colSpan} className="px-3 py-2">
        {error && <p className="text-sm text-status-critical">Could not load items.</p>}
        {!error && items === null && (
          <p className="text-sm text-gray-400 dark:text-gray-500">Loading items…</p>
        )}
        {!error && items?.length === 0 && (
          <p className="text-sm text-gray-400 dark:text-gray-500">No items on this order.</p>
        )}
        {!error && items && items.length > 0 && (
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
                <th className="py-1 pr-3 font-medium">Resource</th>
                <th className="py-1 font-medium">Quantity</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <td className="py-1 pr-3">
                    {resources[item.resourceId]?.name ?? `#${item.resourceId}`}
                  </td>
                  <td className="py-1">{item.quantity}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </td>
    </tr>
  )
}
