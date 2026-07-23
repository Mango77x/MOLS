import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../api/client'
import { extractApiError } from '../api/errors'

function buildSchema(t: TFunction) {
  return z.object({
    newPassword: z
      .string()
      .min(12, t('users.form.passwordLength'))
      .max(128, t('users.form.passwordLength')),
  })
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>

/**
 * Self-service password reset, step 2 — redeems the token from the emailed
 * link (see AuthController.resetPasswordWithToken). Unlike forgot-password's
 * always-succeeds confirmation, a failure here (expired/already-used/malformed
 * token) is shown directly: the token itself, not an email address, so there's
 * nothing left to enumerate by revealing it didn't work.
 */
export default function ResetPasswordPage() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [banner, setBanner] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(buildSchema(t)),
    defaultValues: { newPassword: '' },
  })

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      await api.post('/auth/reset-password', { token, newPassword: values.newPassword })
      setSuccess(true)
    } catch (error) {
      setBanner(extractApiError(error).message)
    }
  }

  return (
    <main className="flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-lg dark:bg-gray-900">
        <h1 className="mb-1 text-2xl font-bold text-army-800 dark:text-army-200">
          {t('resetPassword.title')}
        </h1>

        {!token && <p className="text-sm text-status-critical">{t('resetPassword.missingToken')}</p>}

        {token && success && (
          <>
            <p className="mb-4 text-sm text-status-ok">{t('resetPassword.success')}</p>
            <Link to="/login" className="text-sm font-medium text-army-700 underline dark:text-army-300">
              {t('forgotPassword.backToLogin')}
            </Link>
          </>
        )}

        {token && !success && (
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <p className="mb-2 text-sm text-gray-500 dark:text-gray-400">{t('resetPassword.subtitle')}</p>
            <div>
              <label htmlFor="newPassword" className="mb-1 block text-sm font-medium">
                {t('resetPassword.newPassword')}
              </label>
              <input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                {...register('newPassword')}
                className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-army-500 focus:outline-none focus:ring-2 focus:ring-army-500/30 dark:border-gray-700 dark:bg-gray-800"
              />
              {errors.newPassword && (
                <p role="alert" className="mt-1 text-sm text-status-critical">
                  {errors.newPassword.message}
                </p>
              )}
            </div>

            {banner && (
              <p role="alert" className="text-sm text-status-critical">
                {banner}
              </p>
            )}

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full rounded-lg bg-army-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-army-600 disabled:opacity-60"
            >
              {isSubmitting ? t('common.saving') : t('resetPassword.submit')}
            </button>
          </form>
        )}
      </div>
    </main>
  )
}
