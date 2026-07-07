import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { extractApiError } from '../../api/errors'
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
 * Destructive row action: confirms before calling `onConfirm`, then reports
 * success or failure as a toast (previously both were silent — a failed
 * delete looked identical to a successful one).
 */
export function DeleteAction({
  label,
  onConfirm,
}: {
  label: string
  onConfirm: () => Promise<void> | void
}) {
  const { showToast } = useToast()
  const [pending, setPending] = useState(false)

  async function handleClick() {
    if (!window.confirm(`Delete this ${label}? This cannot be undone.`)) return
    setPending(true)
    try {
      await onConfirm()
      showToast(`${label.charAt(0).toUpperCase()}${label.slice(1)} deleted.`, 'success')
    } catch (error) {
      showToast(extractApiError(error).message, 'error')
    } finally {
      setPending(false)
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={pending}
      className="text-status-critical underline disabled:opacity-60"
    >
      {pending ? 'Deleting…' : 'Delete'}
    </button>
  )
}
