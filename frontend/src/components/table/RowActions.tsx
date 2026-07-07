import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/** Link-styled row action to a still-Thymeleaf page (e.g. not-yet-migrated edit forms). */
export function ActionLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a href={href} className="text-army-700 underline dark:text-army-300">
      {children}
    </a>
  )
}

/** Link-styled row action to a React route (e.g. "Edit"/"View" on a migrated form/detail page). */
export function RouteActionLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="text-army-700 underline dark:text-army-300">
      {children}
    </Link>
  )
}

/** Destructive row action: confirms before calling `onConfirm`. */
export function DeleteAction({
  label,
  onConfirm,
}: {
  label: string
  onConfirm: () => void
}) {
  return (
    <button
      type="button"
      onClick={() => {
        if (window.confirm(`Delete this ${label}? This cannot be undone.`)) onConfirm()
      }}
      className="text-status-critical underline"
    >
      Delete
    </button>
  )
}
