import { useState, type ChangeEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../../api/client'
import { extractApiError } from '../../api/errors'
import type { ImportPreviewResponse, ImportRowStatus } from '../../api/imports'
import Badge, { type BadgeTone } from '../Badge'
import { FormBanner } from '../form/fields'
import { FormPage } from '../form/FormPage'
import { useToast } from '../toast/toastContext'

const STATUS_TONE: Record<ImportRowStatus, BadgeTone> = {
  VALID: 'ok',
  DUPLICATE_WARNING: 'warn',
  ERROR: 'critical',
}

export interface ImportColumn<T> {
  key: string
  header: string
  render: (data: T) => ReactNode
}

/**
 * Shared two-step (preview → commit) bulk CSV import page for the Sprint 20
 * catalog imports (Resources/Warehouses/Units) — the only thing that
 * differs between them is which columns to show and which endpoint to hit,
 * both passed in by the thin per-entity wrapper page.
 */
export default function ImportPage<T>({
  title,
  backTo,
  apiBasePath,
  columns,
  csvColumnsHint,
}: {
  title: string
  backTo: string
  apiBasePath: string
  columns: ImportColumn<T>[]
  csvColumnsHint: string
}) {
  const { t } = useTranslation()
  const { showToast } = useToast()
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<ImportPreviewResponse<T> | null>(null)
  const [committed, setCommitted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [banner, setBanner] = useState<string | null>(null)

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    setFile(event.target.files?.[0] ?? null)
    setResult(null)
    setCommitted(false)
    setBanner(null)
  }

  async function handlePreview() {
    if (!file) return
    setBusy(true)
    setBanner(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const response = await api.post<ImportPreviewResponse<T>>(`${apiBasePath}/import/preview`, formData)
      setResult(response.data)
      setCommitted(false)
    } catch (error) {
      setBanner(extractApiError(error).message)
    } finally {
      setBusy(false)
    }
  }

  async function handleCommit() {
    if (!file) return
    setBusy(true)
    setBanner(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const response = await api.post<ImportPreviewResponse<T>>(`${apiBasePath}/import/commit`, formData)
      setResult(response.data)
      setCommitted(true)
      showToast(
        t('import.committed', { count: response.data.validCount + response.data.duplicateWarningCount }),
        'success',
      )
    } catch (error) {
      setBanner(extractApiError(error).message)
    } finally {
      setBusy(false)
    }
  }

  const canCommit = result !== null && result.errorCount === 0 && !committed

  return (
    <FormPage title={title} backTo={backTo} wide>
      <div className="space-y-4">
        <FormBanner message={banner} />

        <div>
          <label htmlFor="import-file" className="mb-1 block text-sm font-medium">
            {t('import.selectFile')}
          </label>
          <input
            id="import-file"
            type="file"
            accept=".csv,text/csv"
            onChange={handleFileChange}
            className="block w-full text-sm text-gray-700 dark:text-gray-300"
          />
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{csvColumnsHint}</p>
        </div>

        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={handlePreview}
            disabled={!file || busy}
            className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800 disabled:opacity-50"
          >
            {busy ? t('import.processing') : t('import.preview')}
          </button>
          {result && (
            <button
              type="button"
              onClick={handleCommit}
              disabled={!canCommit || busy}
              className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800 disabled:opacity-50"
            >
              {busy
                ? t('import.processing')
                : t('import.commit', { count: result.validCount + result.duplicateWarningCount })}
            </button>
          )}
        </div>

        {result && result.errorCount > 0 && !committed && (
          <p className="text-sm text-status-critical">{t('import.fixErrorsHint')}</p>
        )}
        {committed && <p className="text-sm text-status-ok">{t('import.committedHint')}</p>}

        {result && (
          <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-800">
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500 dark:bg-gray-800/50 dark:text-gray-400">
                <tr>
                  <th className="px-3 py-2 font-medium">{t('import.row')}</th>
                  <th className="px-3 py-2 font-medium">{t('common.status')}</th>
                  {columns.map((col) => (
                    <th key={col.key} className="px-3 py-2 font-medium">
                      {col.header}
                    </th>
                  ))}
                  <th className="px-3 py-2 font-medium">{t('import.details')}</th>
                </tr>
              </thead>
              <tbody>
                {result.rows.length === 0 && (
                  <tr>
                    <td colSpan={columns.length + 3} className="px-3 py-4 text-center text-gray-400 dark:text-gray-500">
                      {t('import.noRows')}
                    </td>
                  </tr>
                )}
                {result.rows.map((row) => (
                  <tr key={row.rowNumber} className="border-t border-gray-100 dark:border-gray-800">
                    <td className="px-3 py-2">{row.rowNumber}</td>
                    <td className="px-3 py-2">
                      <Badge tone={STATUS_TONE[row.status]}>{t(`import.status.${row.status}`)}</Badge>
                    </td>
                    {columns.map((col) => (
                      <td key={col.key} className="px-3 py-2">
                        {row.data ? col.render(row.data) : '—'}
                      </td>
                    ))}
                    <td className="px-3 py-2 text-xs text-gray-500 dark:text-gray-400">
                      {row.errors.join('; ')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </FormPage>
  )
}
