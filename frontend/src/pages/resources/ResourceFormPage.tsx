import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { ResourceEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'

const schema = z.object({
  name: z
    .string()
    .min(2, 'Resource name must be between 2 and 100 characters')
    .max(100, 'Resource name must be between 2 and 100 characters'),
  type: z
    .string()
    .min(2, 'Resource type must be between 2 and 50 characters')
    .max(50, 'Resource type must be between 2 and 50 characters'),
  criticality: z.enum(['LOW', 'MEDIUM', 'HIGH'], { message: 'Select a criticality level' }),
})

type FormValues = z.infer<typeof schema>

export default function ResourceFormPage() {
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const { data: resource, loading, notFound } = useEntity<ResourceEntity>(
    isEdit ? `/resources/${id}` : null,
  )
  const [banner, setBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', type: '', criticality: undefined },
  })

  useEffect(() => {
    if (resource) {
      reset({
        name: resource.name,
        type: resource.type,
        criticality: resource.criticality as FormValues['criticality'],
      })
    }
  }, [resource, reset])

  async function onSubmit(values: FormValues) {
    setBanner(null)
    try {
      if (isEdit) {
        await api.put(`/resources/${id}`, values)
      } else {
        await api.post('/resources', values)
      }
      navigate('/resources')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (isEdit && loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading resource…</p>
  }
  if (isEdit && notFound) {
    return <FormBanner message="Resource not found." />
  }

  return (
    <FormPage title={isEdit ? 'Edit resource' : 'New resource'} backTo="/resources">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <TextField
          label="Name"
          id="name"
          registration={register('name')}
          error={errors.name?.message}
        />
        <TextField
          label="Type"
          id="type"
          placeholder="e.g. Equipment, Material"
          registration={register('type')}
          error={errors.type?.message}
        />
        <SelectField
          label="Criticality"
          id="criticality"
          registration={register('criticality')}
          error={errors.criticality?.message}
        >
          <option value="" disabled>
            Select a level
          </option>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
        </SelectField>
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save'}</SubmitButton>
          <SecondaryButton onClick={() => navigate('/resources')}>Cancel</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
