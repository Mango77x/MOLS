import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { UserEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'

function buildSchema(t: TFunction) {
  return z.object({
    password: z
      .string()
      .min(12, t('users.form.passwordLength'))
      .max(128, t('users.form.passwordLength')),
  })
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>

export default function UserResetPasswordPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const { data: user, loading, notFound } = useEntity<UserEntity>(id ? `/users/${id}` : null)
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(buildSchema(t)),
    defaultValues: { password: '' },
  })

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      await api.patch(`/users/${id}/password`, values)
      navigate('/users')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">{t('users.resetPasswordPage.loading')}</p>
  }
  if (notFound) {
    return <FormBanner message={t('users.resetPasswordPage.notFound')} />
  }

  return (
    <FormPage
      title={
        user
          ? t('users.resetPasswordPage.titleWithUsername', { username: user.username })
          : t('users.resetPasswordPage.title')
      }
      backTo="/users"
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <TextField
          label={t('users.resetPasswordPage.newPassword')}
          id="password"
          type="password"
          autoComplete="new-password"
          hint={t('users.form.passwordHint')}
          registration={register('password')}
          error={errors.password?.message}
        />
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? t('common.saving') : t('common.save')}</SubmitButton>
          <SecondaryButton onClick={() => navigate('/users')}>{t('common.cancel')}</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
