import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../../api/client'
import type { ResourceEntity } from '../../api/entities'
import { applyApiError, extractApiError } from '../../api/errors'
import { useEntity } from '../../api/useEntity'
import { FormBanner, SecondaryButton, SelectField, SubmitButton, TextField } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import { useDuplicateNameWarning } from '../../components/form/useDuplicateNameWarning'
import { useToast } from '../../components/toast/toastContext'

function buildSchema(t: TFunction) {
  return z.object({
    name: z
      .string()
      .min(2, t('resources.form.nameLength'))
      .max(100, t('resources.form.nameLength')),
    type: z
      .string()
      .min(2, t('resources.form.typeLength'))
      .max(50, t('resources.form.typeLength')),
    criticality: z.enum(['LOW', 'MEDIUM', 'HIGH'], { message: t('resources.form.selectCriticality') }),
  })
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>

export default function ResourceFormPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { data: resource, loading, notFound } = useEntity<ResourceEntity>(
    isEdit ? `/resources/${id}` : null,
  )
  const [banner, setBanner] = useState<string | null>(null)
  const { warning: nameWarning, checkName } = useDuplicateNameWarning(
    '/resources',
    t('resources.entityName'),
    isEdit ? Number(id) : undefined,
  )

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(buildSchema(t)),
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
        showToast(t('resources.updated'), 'success')
      } else {
        await api.post('/resources', values)
        showToast(t('resources.created'), 'success')
      }
      navigate('/resources')
    } catch (error) {
      setBanner(applyApiError(extractApiError(error), setError))
    }
  }

  if (isEdit && loading) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">{t('resources.loading')}</p>
  }
  if (isEdit && notFound) {
    return <FormBanner message={t('resources.notFound')} />
  }

  return (
    <FormPage title={isEdit ? t('resources.editResource') : t('resources.newResource')} backTo="/resources">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormBanner message={banner} />
        <TextField
          label={t('common.name')}
          id="name"
          registration={register('name', { onBlur: (e) => checkName(e.target.value) })}
          error={errors.name?.message}
          warning={nameWarning}
        />
        <TextField
          label={t('common.type')}
          id="type"
          placeholder={t('resources.form.typePlaceholder')}
          registration={register('type')}
          error={errors.type?.message}
        />
        <SelectField
          label={t('resources.criticality')}
          id="criticality"
          registration={register('criticality')}
          error={errors.criticality?.message}
          defaultValue=""
        >
          <option value="" disabled>
            {t('resources.form.selectLevel')}
          </option>
          <option value="LOW">{t('enums.criticality.LOW')}</option>
          <option value="MEDIUM">{t('enums.criticality.MEDIUM')}</option>
          <option value="HIGH">{t('enums.criticality.HIGH')}</option>
        </SelectField>
        <div className="flex gap-3">
          <SubmitButton submitting={isSubmitting}>
            {isSubmitting ? t('common.saving') : t('common.save')}
          </SubmitButton>
          <SecondaryButton onClick={() => navigate('/resources')}>{t('common.cancel')}</SecondaryButton>
        </div>
      </form>
    </FormPage>
  )
}
