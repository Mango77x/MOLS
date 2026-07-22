import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { extractApiError } from '../../api/errors'
import ConfirmDialog from '../ConfirmDialog'
import { useToast } from '../toast/toastContext'

/** Link-styled row action to a React route (e.g. "Edit"/"View" on a form/detail page). */
export function RouteActionLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="text-army-700 underline dark:text-army-300">
      {children}
    </Link>
  )
}

/**
 * Destructive row action: confirms via a styled dialog (not the native
 * `window.confirm()`, which looks jarring next to the rest of the themed
 * UI) before calling `onConfirm`, then reports success or failure as a
 * toast (previously both were silent — a failed delete looked identical to
 * a successful one).
 */
export function DeleteAction({
  label,
  onConfirm,
}: {
  label: string
  onConfirm: () => Promise<void> | void
}) {
  const { t } = useTranslation()
  const { showToast } = useToast()
  const [pending, setPending] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)

  async function handleConfirm() {
    setConfirmOpen(false)
    setPending(true)
    try {
      await onConfirm()
      showToast(
        t('rowActions.deleted', { label: label.charAt(0).toUpperCase() + label.slice(1) }),
        'success',
      )
    } catch (error) {
      showToast(extractApiError(error).message, 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setConfirmOpen(true)}
        disabled={pending}
        className="text-status-critical underline disabled:opacity-60"
      >
        {pending ? t('common.deleting') : t('common.delete')}
      </button>
      <ConfirmDialog
        open={confirmOpen}
        title={t('rowActions.deleteThis', { label })}
        message={t('rowActions.cannotBeUndone')}
        onConfirm={handleConfirm}
        onCancel={() => setConfirmOpen(false)}
      />
    </>
  )
}
