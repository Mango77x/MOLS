import { api } from './client'
import type { Filters, PageResponse, SortState } from '../components/table/useServerTable'

/**
 * Fetches every page of a paginated endpoint for the current filters/sort,
 * looping at the server's own page-size cap (`PageQuery.MAX_SIZE` = 100)
 * instead of requesting an uncapped export endpoint that doesn't exist yet.
 * Used for CSV export, where the user wants the full filtered result set,
 * not just the page currently on screen.
 */
export async function fetchAllPages<T>(path: string, filters: Filters, sort?: SortState): Promise<T[]> {
  const size = 100
  const all: T[] = []
  let page = 0
  for (;;) {
    const params: Record<string, string | number> = { page, size }
    if (sort) params.sort = `${sort.field}${sort.desc ? ',desc' : ''}`
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== '') params[key] = value
    }
    const response = await api.get<PageResponse<T>>(path, { params })
    all.push(...response.data.content)
    if (response.data.content.length === 0 || page >= response.data.totalPages - 1) break
    page += 1
  }
  return all
}
