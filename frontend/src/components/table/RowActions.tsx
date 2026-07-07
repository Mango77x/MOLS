import type { ReactNode } from 'react'

/** Link-styled row action (e.g. "Edit" → the existing Thymeleaf edit page). */
export function ActionLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a href={href} className="text-army-700 underline dark:text-army-300">
      {children}
    </a>
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
