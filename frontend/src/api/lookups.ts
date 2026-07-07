import { useEffect, useState } from 'react'
import { api } from './client'

/**
 * Fetches the plain-array (non-paginated) contract of a list endpoint and
 * indexes it by `id`, for tables that need to resolve a foreign id (e.g. a
 * stock's `warehouseId`) to a display name. Reference data (warehouses,
 * resources, units, vehicles, orders) is small enough to load in full.
 */
export function useLookup<T extends { id: number }>(path: string) {
  const [byId, setById] = useState<Record<number, T>>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    api
      .get<T[]>(path)
      .then((response) => {
        if (cancelled) return
        const map: Record<number, T> = {}
        for (const item of response.data) map[item.id] = item
        setById(map)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [path])

  return { byId, loading }
}
