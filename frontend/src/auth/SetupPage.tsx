import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { extractApiError } from '../api/errors'
import { useSetupStatus } from './useSetupStatus'

/**
 * First-run setup: creates the very first ADMIN user. React replacement for
 * the old Thymeleaf {@code /ui/setup} page (Sprint 6 cutover) — only
 * reachable while the database has zero application users; once one exists,
 * redirects to /login like the old page did.
 */
export default function SetupPage() {
  const needsSetup = useSetupStatus()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (needsSetup === null) {
    return null
  }
  if (needsSetup === false) {
    return <Navigate to="/login" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await api.post('/auth/setup', { username, password })
      navigate('/login', { replace: true, state: { justCreated: true } })
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-lg dark:bg-gray-900">
        <h1 className="mb-1 text-2xl font-bold text-army-800 dark:text-army-200">MOLS</h1>
        <p className="mb-6 text-sm text-gray-500 dark:text-gray-400">
          First-run setup — create the initial administrator account.
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
              autoComplete="new-password"
              required
              minLength={12}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-army-500 focus:outline-none focus:ring-2 focus:ring-army-500/30 dark:border-gray-700 dark:bg-gray-800"
            />
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">At least 12 characters.</p>
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
            {submitting ? 'Creating…' : 'Create administrator account'}
          </button>
        </form>
      </div>
    </main>
  )
}
