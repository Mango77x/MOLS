import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from './store'
import { useSetupStatus } from './useSetupStatus'

/**
 * Login form for the SPA. On success the API sets the HttpOnly auth cookie
 * and the store marks the session as authenticated; the router then sends
 * the user back to where they came from.
 */
export default function LoginPage() {
  const { t } = useTranslation()
  const status = useAuthStore((state) => state.status)
  const login = useAuthStore((state) => state.login)
  const location = useLocation()
  const needsSetup = useSetupStatus()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }

  // Mirrors the old Thymeleaf /ui/login behavior: no application users exist
  // yet, so send the visitor to first-run setup instead of a login form.
  if (needsSetup === null) {
    return null
  }
  if (needsSetup === true) {
    return <Navigate to="/setup" replace />
  }

  const justCreated = (location.state as { justCreated?: boolean } | null)?.justCreated ?? false

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(username, password)
    } catch {
      setError(t('login.invalidCredentials'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-lg dark:bg-gray-900">
        <h1 className="mb-1 text-2xl font-bold text-army-800 dark:text-army-200">{t('login.title')}</h1>
        <p className="mb-6 text-sm text-gray-500 dark:text-gray-400">{t('login.subtitle')}</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          {justCreated && (
            <output className="block text-sm text-status-ok">{t('login.adminCreated')}</output>
          )}
          <div>
            <label htmlFor="username" className="mb-1 block text-sm font-medium">
              {t('login.username')}
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
              {t('login.password')}
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
            {submitting ? t('login.signingIn') : t('login.signIn')}
          </button>
        </form>

        <p className="mt-4 text-center text-sm">
          <Link to="/forgot-password" className="text-army-700 underline dark:text-army-300">
            {t('login.forgotPassword')}
          </Link>
        </p>

        <p className="mt-2 text-center text-xs text-gray-500 dark:text-gray-400">{t('login.lostAccessHint')}</p>
      </div>
    </main>
  )
}
