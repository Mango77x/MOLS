import { useTranslation } from 'react-i18next'
import type { CreateUnitRequest } from '../../api/imports'
import ImportPage, { type ImportColumn } from '../../components/imports/ImportPage'

export default function UnitImportPage() {
  const { t } = useTranslation()

  const columns: ImportColumn<CreateUnitRequest>[] = [
    { key: 'name', header: t('common.name'), render: (d) => d.name },
    { key: 'location', header: t('common.location'), render: (d) => d.location },
    { key: 'latitude', header: t('common.latitude'), render: (d) => d.latitude ?? '—' },
    { key: 'longitude', header: t('common.longitude'), render: (d) => d.longitude ?? '—' },
  ]

  return (
    <ImportPage
      title={t('units.importTitle')}
      backTo="/units"
      apiBasePath="/units"
      columns={columns}
      csvColumnsHint={t('units.importColumnsHint')}
    />
  )
}
