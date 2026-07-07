import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api/client'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface SortState {
  field: string
  desc: boolean
}

export type Filters = Record<string, string | number | undefined>

/**
 * Drives a server-paginated table: owns page/size/sort/filter state and
 * fetches `GET path` with those as query params (always passing `page`, so
 * the API always answers with the PageResponse envelope, never the plain
 * array contract).
 */
export function useServerTable<T>(path: string, filters: Filters, defaultSort?: SortState) {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [sort, setSort] = useState<SortState | undefined>(defaultSort)
  const [data, setData] = useState<PageResponse<T> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  const filterKey = JSON.stringify(filters)

  // Filters changed: go back to page 0 so the user doesn't land on an
  // out-of-range page for the new result set.
  useEffect(() => {
    setPage(0)
  }, [filterKey])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    const params: Record<string, string | number> = { page, size }
    if (sort) params.sort = `${sort.field}${sort.desc ? ',desc' : ''}`
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== '') params[key] = value
    }
    api
      .get<PageResponse<T>>(path, { params })
      .then((response) => {
        if (!cancelled) {
          setData(response.data)
          setError(false)
        }
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- filterKey stands in for filters
  }, [path, page, size, sort, filterKey, reloadToken])

  const reload = useCallback(() => setReloadToken((n) => n + 1), [])

  function toggleSort(field: string) {
    setSort((current) => {
      if (!current || current.field !== field) return { field, desc: false }
      if (!current.desc) return { field, desc: true }
      return undefined
    })
  }

  return {
    rows: data?.content ?? [],
    page,
    size,
    totalPages: data?.totalPages ?? 0,
    totalElements: data?.totalElements ?? 0,
    setPage,
    setSize,
    sort,
    toggleSort,
    loading,
    error,
    reload,
  }
}
