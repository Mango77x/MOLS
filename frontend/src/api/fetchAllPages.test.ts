import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { fetchAllPages } from './fetchAllPages'
import type { PageResponse } from '../components/table/useServerTable'

function page(content: number[], page: number, totalPages: number): { data: PageResponse<number> } {
  return { data: { content, page, size: 100, totalElements: totalPages * 100, totalPages } }
}

describe('fetchAllPages', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns all content when everything fits on one page', async () => {
    vi.spyOn(api, 'get').mockResolvedValue(page([1, 2, 3], 0, 1))
    const result = await fetchAllPages<number>('/things', {})
    expect(result).toEqual([1, 2, 3])
  })

  it('loops across pages until the last one and concatenates content', async () => {
    const getSpy = vi
      .spyOn(api, 'get')
      .mockResolvedValueOnce(page([1, 2], 0, 3))
      .mockResolvedValueOnce(page([3, 4], 1, 3))
      .mockResolvedValueOnce(page([5], 2, 3))
    const result = await fetchAllPages<number>('/things', {})
    expect(result).toEqual([1, 2, 3, 4, 5])
    expect(getSpy).toHaveBeenCalledTimes(3)
  })

  it('stops on an empty page even if totalPages claims more (defensive against an inconsistent response)', async () => {
    vi.spyOn(api, 'get').mockResolvedValueOnce(page([], 0, 5))
    const result = await fetchAllPages<number>('/things', {})
    expect(result).toEqual([])
  })

  it('forwards non-empty filters and the sort param, dropping empty-string/undefined filters', async () => {
    const getSpy = vi.spyOn(api, 'get').mockResolvedValue(page([1], 0, 1))
    await fetchAllPages<number>('/things', { status: 'CREATED', unitId: '', orderId: undefined }, {
      field: 'id',
      desc: true,
    })
    expect(getSpy).toHaveBeenCalledWith('/things', {
      params: { page: 0, size: 100, sort: 'id,desc', status: 'CREATED' },
    })
  })

  it('requests size=100, the server-enforced page-size cap', async () => {
    const getSpy = vi.spyOn(api, 'get').mockResolvedValue(page([1], 0, 1))
    await fetchAllPages<number>('/things', {})
    expect(getSpy).toHaveBeenCalledWith('/things', { params: { page: 0, size: 100 } })
  })
})
