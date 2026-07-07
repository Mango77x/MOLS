import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import { applyApiError, extractApiError } from '../../api/errors'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'

const schema = z.object({
  username: z
    .string()
    .min(3, 'Username must be between 3 and 50 characters')
    .max(50, 'Username must be between 3 and 50 characters'),
  password: z
    .string()
    .min(12, 'Password must be between 12 and 128 characters')
    .max(128, 'Password must be between 12 and 128 characters'),
  role: z.enum(['ADMIN', 'OPERATOR', 'AUDITOR'], { message: 'Select a role' }),
})

type FormValues = z.infer<typeof schema>

export default function UserFormPage() {
  const navigate = useNavigate()
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: '', password: '', role: undefined },
  })

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      await api.post('/users', values)
      navigate('/users')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  return (
    <FormPage title="New user" backTo="/users">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <TextField
          label="Username"
          id="username"
          registration={register('username')}
          error={errors.username?.message}
        />
        <TextField
          label="Password"
          id="password"
          type="password"
          autoComplete="new-password"
          hint="At least 12 characters."
          registration={register('password')}
          error={errors.password?.message}
        />
        <SelectField label="Role" id="role" registration={register('role')} error={errors.role?.message}>
          <option value="" disabled>
            Select a role
          </option>
          <option value="ADMIN">Admin</option>
          <option value="OPERATOR">Operator</option>
          <option value="AUDITOR">Auditor</option>
        </SelectField>
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save'}</SubmitButton>
          <SecondaryButton onClick={() => navigate('/users')}>Cancel</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
