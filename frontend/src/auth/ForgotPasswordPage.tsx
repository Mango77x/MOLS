import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { api } from '../api/client'

/**
 * Self-service password reset, step 1. The backend always answers 200
 * regardless of whether the email matches an account (avoids account
 * enumeration) — this page mirrors that by always showing the same
 * "check your email" message rather than a success/failure branch.
 */
export default function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await api.post('/auth/forgot-password', { email })
    } catch {
      // Deliberately ignored — see the module doc: the outcome is always
      // the same generic confirmation, regardless of what the API returns.
    } finally {
      setSubmitting(false)
      setSubmitted(true)
    }
  }

  return (
    <main className="flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-lg dark:bg-gray-900">
        <h1 className="mb-1 text-2xl font-bold text-army-800 dark:text-army-200">
          {t('forgotPassword.title')}
        </h1>
        <p className="mb-6 text-sm text-gray-500 dark:text-gray-400">{t('forgotPassword.subtitle')}</p>

        {submitted ? (
          <p className="text-sm text-status-ok">{t('forgotPassword.confirmation')}</p>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="email" className="mb-1 block text-sm font-medium">
                {t('common.email')}
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-army-500 focus:outline-none focus:ring-2 focus:ring-army-500/30 dark:border-gray-700 dark:bg-gray-800"
              />
            </div>
            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-lg bg-army-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-army-600 disabled:opacity-60"
            >
              {submitting ? t('forgotPassword.sending') : t('forgotPassword.send')}
            </button>
          </form>
        )}

        <p className="mt-6 text-center text-xs text-gray-500 dark:text-gray-400">
          <Link to="/login" className="text-army-700 underline dark:text-army-300">
            {t('forgotPassword.backToLogin')}
          </Link>
        </p>
      </div>
    </main>
  )
}
