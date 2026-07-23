import { useTranslation } from 'react-i18next'
import type { CreateResourceRequest } from '../../api/imports'
import ImportPage, { type ImportColumn } from '../../components/imports/ImportPage'

export default function ResourceImportPage() {
  const { t } = useTranslation()

  const columns: ImportColumn<CreateResourceRequest>[] = [
    { key: 'name', header: t('common.name'), render: (d) => d.name },
    { key: 'type', header: t('common.type'), render: (d) => d.type },
    { key: 'criticality', header: t('resources.criticality'), render: (d) => d.criticality },
  ]

  return (
    <ImportPage
      title={t('resources.importTitle')}
      backTo="/resources"
      apiBasePath="/resources"
      columns={columns}
      csvColumnsHint={t('resources.importColumnsHint')}
    />
  )
}
