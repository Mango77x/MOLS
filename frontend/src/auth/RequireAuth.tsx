import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore, type Role } from './store'

/**
 * Route guard: renders children only for an authenticated session.
 * While the initial session restore is in flight, renders nothing (avoids
 * a login flash on reload). Anonymous users are sent to /login, remembering
 * where they were headed.
 *
 * With `roles`, users whose role isn't in the list are bounced to the
 * dashboard — this is a UX guard only; the real enforcement lives in the API
 * role matrix.
 */
export default function RequireAuth({
  children,
  roles,
}: {
  children: ReactNode
  roles?: Role[]
}) {
  const status = useAuthStore((state) => state.status)
  const user = useAuthStore((state) => state.user)
  const location = useLocation()

  if (status === 'unknown') {
    return null
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (roles && (!user?.role || !roles.includes(user.role))) {
    return <Navigate to="/" replace />
  }

  return children
}
