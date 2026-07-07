import { useEffect, useState } from 'react'
import { api } from './client'

/**
 * Fetches a single entity by API path (e.g. `/warehouses/5`), for edit-mode
 * forms and detail pages that need to preload existing data. Pass `null` to
 * skip fetching (create mode).
 */
export function useEntity<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(!!path)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    if (!path) {
      setLoading(false)
      return
    }
    let cancelled = false
    setLoading(true)
    setNotFound(false)
    api
      .get<T>(path)
      .then((response) => {
        if (!cancelled) setData(response.data)
      })
      .catch((error) => {
        if (!cancelled && error?.response?.status === 404) setNotFound(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [path])

  return { data, loading, notFound }
}
