import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/** Shared page chrome for a single-card create/edit form: title + back link + card. */
export function FormPage({
  title,
  backTo,
  backLabel = 'Back',
  wide = false,
  children,
}: {
  title: string
  backTo: string
  backLabel?: string
  wide?: boolean
  children: ReactNode
}) {
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">{title}</h1>
        <Link to={backTo} className="text-sm font-medium text-army-700 underline dark:text-army-300">
          {backLabel}
        </Link>
      </div>
      <div className={`${wide ? 'max-w-3xl' : 'max-w-xl'} rounded-xl bg-white p-6 shadow-sm dark:bg-gray-900`}>
        {children}
      </div>
    </div>
  )
}
