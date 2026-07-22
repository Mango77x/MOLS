import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useToast } from '../toast/toastContext'

/** Button that runs an async CSV export (fetch-all-pages + download), with a loading state and an error toast. */
export default function ExportCsvButton({ onExport }: { onExport: () => Promise<void> }) {
  const { t } = useTranslation()
  const { showToast } = useToast()
  const [exporting, setExporting] = useState(false)

  async function handleClick() {
    setExporting(true)
    try {
      await onExport()
    } catch {
      showToast(t('common.exportFailed'), 'error')
    } finally {
      setExporting(false)
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={exporting}
      className="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
    >
      {exporting ? t('common.exporting') : t('common.exportCsv')}
    </button>
  )
}
