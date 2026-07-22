import { useState } from 'react'
import { api } from '../../api/client'
import type { PageResponse } from '../table/useServerTable'

/**
 * Non-blocking duplicate-name nudge for Warehouse/Resource/Unit forms.
 * There's no uniqueness constraint on these names in the database, so this
 * is only a courtesy warning — it must never block submit. `path`'s `name`
 * filter is a case-insensitive fragment match (see useServerTable), so the
 * exact match is checked client-side against the returned page.
 */
export function useDuplicateNameWarning(path: string, label: string, excludeId?: number) {
  const [warning, setWarning] = useState<string | null>(null)

  async function checkName(name: string) {
    const trimmed = name.trim()
    if (!trimmed) {
      setWarning(null)
      return
    }
    try {
      // 100 matches the app's own "large page" convention (DataTable's own
      // max page-size option) — large enough to cover realistic catalogs,
      // without a second unpaginated backend contract just for this check.
      const response = await api.get<PageResponse<{ id: number; name: string }>>(path, {
        params: { name: trimmed, page: 0, size: 100 },
      })
      const duplicate = response.data.content.some(
        (row) => row.id !== excludeId && row.name.toLowerCase() === trimmed.toLowerCase(),
      )
      setWarning(duplicate ? `A ${label} named "${trimmed}" already exists.` : null)
    } catch {
      // Best-effort nudge only — a failed lookup shouldn't block or alarm the user.
      setWarning(null)
    }
  }

  return { warning, checkName }
}
