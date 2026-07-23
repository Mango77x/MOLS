import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import { applyApiError, extractApiError } from '../../api/errors'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { useToast } from '../../components/toast/toastContext'
import { enumLabel, ROLE_LABELS } from '../../lib/enumLabels'

function buildSchema(t: TFunction) {
  return z.object({
    username: z
      .string()
      .min(3, t('users.form.usernameLength'))
      .max(50, t('users.form.usernameLength')),
    password: z
      .string()
      .min(12, t('users.form.passwordLength'))
      .max(128, t('users.form.passwordLength')),
    role: z.enum(['ADMIN', 'OPERATOR', 'AUDITOR'], { message: t('users.form.selectRole') }),
    email: z.string().email(t('users.form.invalidEmail')).or(z.literal('')),
  })
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>

export default function UserFormPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(buildSchema(t)),
    defaultValues: { username: '', password: '', role: undefined, email: '' },
  })

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      await api.post('/users', { ...values, email: values.email || undefined })
      showToast(t('users.created'), 'success')
      navigate('/users')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  return (
    <FormPage title={t('users.newUser')} backTo="/users">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <TextField
          label={t('users.username')}
          id="username"
          registration={register('username')}
          error={errors.username?.message}
        />
        <TextField
          label={t('common.password')}
          id="password"
          type="password"
          autoComplete="new-password"
          hint={t('users.form.passwordHint')}
          registration={register('password')}
          error={errors.password?.message}
        />
        <SelectField
          label={t('users.role')}
          id="role"
          registration={register('role')}
          error={errors.role?.message}
          defaultValue=""
        >
          <option value="" disabled>
            {t('users.form.selectRole')}
          </option>
          {Object.entries(ROLE_LABELS).map(([value]) => (
            <option key={value} value={value}>
              {enumLabel(ROLE_LABELS, value)}
            </option>
          ))}
        </SelectField>
        <TextField
          label={t('users.form.emailOptional')}
          id="email"
          type="email"
          autoComplete="email"
          hint={t('users.form.emailHint')}
          registration={register('email')}
          error={errors.email?.message}
        />
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? t('common.saving') : t('common.save')}</SubmitButton>
          <SecondaryButton onClick={() => navigate('/users')}>{t('common.cancel')}</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
