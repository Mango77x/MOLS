import { useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from './store'

/**
 * Login form for the SPA. On success the API sets the HttpOnly auth cookie
 * and the store marks the session as authenticated; the router then sends
 * the user back to where they came from.
 */
export default function LoginPage() {
  const status = useAuthStore((state) => state.status)
  const login = useAuthStore((state) => state.login)
  const location = useLocation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(username, password)
    } catch {
      setError('Invalid username or password.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-lg dark:bg-gray-900">
        <h1 className="mb-1 text-2xl font-bold text-army-800 dark:text-army-200">MOLS</h1>
        <p className="mb-6 text-sm text-gray-500 dark:text-gray-400">
          Multimodal Operative Logistics System
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="username" className="mb-1 block text-sm font-medium">
              Username
            </label>
            <input
              id="username"
              type="text"
              autoComplete="username"
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-army-500 focus:outline-none focus:ring-2 focus:ring-army-500/30 dark:border-gray-700 dark:bg-gray-800"
            />
          </div>

          <div>
            <label htmlFor="password" className="mb-1 block text-sm font-medium">
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-army-500 focus:outline-none focus:ring-2 focus:ring-army-500/30 dark:border-gray-700 dark:bg-gray-800"
            />
          </div>

          {error && (
            <p role="alert" className="text-sm text-status-critical">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-lg bg-army-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-army-600 disabled:opacity-60"
          >
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </main>
  )
}
