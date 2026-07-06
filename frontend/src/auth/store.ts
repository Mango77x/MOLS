import { create } from 'zustand'
import { api, setUnauthorizedHandler } from '../api/client'

export type Role = 'ADMIN' | 'OPERATOR' | 'AUDITOR'

export interface SessionUser {
  username: string
  role: Role
}

export type AuthStatus =
  /** Session not checked yet (first load) — show nothing until restore() settles. */
  | 'unknown'
  | 'authenticated'
  | 'anonymous'

interface AuthState {
  status: AuthStatus
  user: SessionUser | null
  /** Marks the session as active (after login or a successful /auth/me). */
  setSession: (user: SessionUser) => void
  /** Drops the client-side session (logout, or the API answered 401). */
  clearSession: () => void
  /** Authenticates against the API; the JWT arrives as an HttpOnly cookie. */
  login: (username: string, password: string) => Promise<void>
  /** Clears the auth cookie server-side and the local session. */
  logout: () => Promise<void>
  /** Restores the session from the cookie on app start via GET /auth/me. */
  restore: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'unknown',
  user: null,

  setSession: (user) => set({ status: 'authenticated', user }),

  clearSession: () => set({ status: 'anonymous', user: null }),

  login: async (username, password) => {
    const { data } = await api.post<{ username: string; role: Role }>('/auth/login', {
      username,
      password,
    })
    set({ status: 'authenticated', user: { username: data.username, role: data.role } })
  },

  logout: async () => {
    try {
      await api.post('/auth/logout')
    } finally {
      set({ status: 'anonymous', user: null })
    }
  },

  restore: async () => {
    try {
      const { data } = await api.get<{ username: string; role: Role }>('/auth/me')
      set({ status: 'authenticated', user: { username: data.username, role: data.role } })
    } catch {
      set({ status: 'anonymous', user: null })
    }
  },
}))

// An expired/invalid cookie surfaces as a 401 on any API call: drop the
// session so the router redirects to login.
setUnauthorizedHandler(() => useAuthStore.getState().clearSession())
