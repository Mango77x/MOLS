import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { UserEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'

const schema = z.object({
  password: z
    .string()
    .min(12, 'Password must be between 12 and 128 characters')
    .max(128, 'Password must be between 12 and 128 characters'),
})

type FormValues = z.infer<typeof schema>

export default function UserResetPasswordPage() {
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
    resolver: zodResolver(schema),
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
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading user…</p>
  }
  if (notFound) {
    return <FormBanner message="User not found." />
  }

  return (
    <FormPage title={user ? `Reset password — ${user.username}` : 'Reset password'} backTo="/users">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <TextField
          label="New password"
          id="password"
          type="password"
          autoComplete="new-password"
          hint="At least 12 characters."
          registration={register('password')}
          error={errors.password?.message}
        />
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save'}</SubmitButton>
          <SecondaryButton onClick={() => navigate('/users')}>Cancel</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
