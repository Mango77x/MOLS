import { useTranslation } from 'react-i18next'
import type { CreateWarehouseRequest } from '../../api/imports'
import ImportPage, { type ImportColumn } from '../../components/imports/ImportPage'

export default function WarehouseImportPage() {
  const { t } = useTranslation()

  const columns: ImportColumn<CreateWarehouseRequest>[] = [
    { key: 'name', header: t('common.name'), render: (d) => d.name },
    { key: 'location', header: t('common.location'), render: (d) => d.location },
    { key: 'latitude', header: t('common.latitude'), render: (d) => d.latitude ?? '—' },
    { key: 'longitude', header: t('common.longitude'), render: (d) => d.longitude ?? '—' },
  ]

  return (
    <ImportPage
      title={t('warehouses.importTitle')}
      backTo="/warehouses"
      apiBasePath="/warehouses"
      columns={columns}
      csvColumnsHint={t('warehouses.importColumnsHint')}
    />
  )
}
