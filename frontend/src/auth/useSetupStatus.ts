import { useEffect, useState } from 'react'
import { api } from '../api/client'

/**
 * Whether the app still needs its first-run setup (no application users
 * exist yet). Returns `null` while the check is in flight — callers should
 * render nothing until it resolves, to avoid a login/setup page flash.
 */
export function useSetupStatus(): boolean | null {
  const [needsSetup, setNeedsSetup] = useState<boolean | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .get<{ needsSetup: boolean }>('/auth/setup-status')
      .then((response) => {
        if (!cancelled) setNeedsSetup(response.data.needsSetup)
      })
      .catch(() => {
        if (!cancelled) setNeedsSetup(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return needsSetup
}
