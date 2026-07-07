import { useAuthStore, type Role } from './store'

/** True when `role` is one of `allowed`. Mirrors Thymeleaf's `sec:authorize="hasAnyRole(...)"`. */
export function hasAnyRole(role: Role | undefined | null, allowed: Role[]): boolean {
  return !!role && allowed.includes(role)
}

/** Convenience hook: the current session's role, or null when signed out. */
export function useCurrentRole(): Role | null {
  return useAuthStore((state) => state.user?.role ?? null)
}
