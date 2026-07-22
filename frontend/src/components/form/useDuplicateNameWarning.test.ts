import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../api/client'
import { useDuplicateNameWarning } from './useDuplicateNameWarning'

function mockNameLookup(rows: { id: number; name: string }[]) {
  return vi.spyOn(api, 'get').mockResolvedValue({
    data: { content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 },
  })
}

/**
 * Sprint 14: there's no uniqueness constraint on Warehouse/Resource/Unit
 * names, so two records can silently share a name today. This is a
 * non-blocking nudge only — it must never surface as a validation error
 * or stop a submit.
 */
describe('useDuplicateNameWarning', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('warns when the fragment-matched results contain an exact case-insensitive match', async () => {
    mockNameLookup([{ id: 1, name: 'Almacén Alpha' }])
    const { result } = renderHook(() => useDuplicateNameWarning('/warehouses', 'warehouse', undefined))

    await act(() => result.current.checkName('almacén alpha'))

    await waitFor(() => expect(result.current.warning).toBe('A warehouse named "almacén alpha" already exists.'))
  })

  it('does not warn when the match is only a fragment, not an exact name', async () => {
    mockNameLookup([{ id: 1, name: 'Almacén Alpha Norte' }])
    const { result } = renderHook(() => useDuplicateNameWarning('/warehouses', 'warehouse', undefined))

    await act(() => result.current.checkName('Alpha'))

    expect(result.current.warning).toBeNull()
  })

  it('excludes the record\'s own id (editing a warehouse does not warn against itself)', async () => {
    mockNameLookup([{ id: 5, name: 'Almacén Alpha' }])
    const { result } = renderHook(() => useDuplicateNameWarning('/warehouses', 'warehouse', 5))

    await act(() => result.current.checkName('Almacén Alpha'))

    expect(result.current.warning).toBeNull()
  })

  it('clears the warning for an empty name', async () => {
    mockNameLookup([{ id: 1, name: 'Almacén Alpha' }])
    const { result } = renderHook(() => useDuplicateNameWarning('/warehouses', 'warehouse', undefined))

    await act(() => result.current.checkName('Almacén Alpha'))
    await waitFor(() => expect(result.current.warning).not.toBeNull())

    await act(() => result.current.checkName('   '))
    expect(result.current.warning).toBeNull()
  })

  it('does not throw and clears the warning if the lookup request fails', async () => {
    vi.spyOn(api, 'get').mockRejectedValue(new Error('network error'))
    const { result } = renderHook(() => useDuplicateNameWarning('/warehouses', 'warehouse', undefined))

    await act(() => result.current.checkName('Almacén Alpha'))

    expect(result.current.warning).toBeNull()
  })

  /**
   * Regression guard: this used to request only the first 20 fragment
   * matches, so an exact duplicate past that page went undetected on
   * larger catalogs. 100 matches the app's own "large page" convention.
   */
  it('requests a large enough page to cover realistic catalogs, not just the first 20', async () => {
    const getSpy = mockNameLookup([])
    const { result } = renderHook(() => useDuplicateNameWarning('/warehouses', 'warehouse', undefined))

    await act(() => result.current.checkName('Almacén Alpha'))

    expect(getSpy).toHaveBeenCalledWith('/warehouses', {
      params: { name: 'Almacén Alpha', page: 0, size: 100 },
    })
  })
})
