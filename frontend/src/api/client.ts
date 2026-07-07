import axios from 'axios'

/**
 * Shared API client.
 *
 * Authentication rides on the HttpOnly `MOLS_AUTH` cookie set by
 * POST /api/auth/login — the token never touches script-accessible storage,
 * so there is no Authorization header to manage here.
 */
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

let unauthorizedHandler: (() => void) | null = null

/**
 * Registers the callback invoked when the API answers 401 (session no longer
 * valid). Wired by the auth store to clear the session, which sends the user
 * back to the login page. Registered lazily to avoid an import cycle between
 * this module and the store.
 */
export function setUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      unauthorizedHandler?.()
    }
    return Promise.reject(error)
  },
)
