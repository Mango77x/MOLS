import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'
import type { UseFormRegisterReturn } from 'react-hook-form'

const inputClass =
  'w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-army-500 focus:outline-none focus:ring-2 focus:ring-army-500/30 dark:border-gray-700 dark:bg-gray-800 disabled:opacity-60'

/** Per-field validation message, styled to match the API's field-error format. */
export function FieldError({ message }: { message?: string }) {
  if (!message) return null
  return (
    <p role="alert" className="mt-1 text-xs text-status-critical">
      {message}
    </p>
  )
}

/** Form-level error banner (business conflicts: 404/409, or a generic failure). */
export function FormBanner({ message }: { message?: string | null }) {
  if (!message) return null
  return (
    <p
      role="alert"
      className="rounded-lg border border-status-critical/30 bg-status-critical/10 px-3 py-2 text-sm text-status-critical"
    >
      {message}
    </p>
  )
}

type TextFieldProps = {
  label: string
  id: string
  registration: UseFormRegisterReturn
  error?: string
  hint?: string
} & InputHTMLAttributes<HTMLInputElement>

/** Text/number/date input wired to react-hook-form's `register`. */
export function TextField({ label, id, registration, error, hint, ...rest }: TextFieldProps) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium">
        {label}
      </label>
      <input id={id} className={inputClass} {...registration} {...rest} />
      {hint && !error && <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{hint}</p>}
      <FieldError message={error} />
    </div>
  )
}

type SelectFieldProps = {
  label: string
  id: string
  registration: UseFormRegisterReturn
  error?: string
  children: ReactNode
} & SelectHTMLAttributes<HTMLSelectElement>

/** Select input wired to react-hook-form's `register`. */
export function SelectField({ label, id, registration, error, children, ...rest }: SelectFieldProps) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium">
        {label}
      </label>
      <select id={id} className={inputClass} {...registration} {...rest}>
        {children}
      </select>
      <FieldError message={error} />
    </div>
  )
}

export function SubmitButton({
  submitting,
  children,
}: {
  submitting: boolean
  children: ReactNode
}) {
  return (
    <button
      type="submit"
      disabled={submitting}
      className="rounded-lg bg-army-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-army-600 disabled:opacity-60"
    >
      {children}
    </button>
  )
}

export function SecondaryButton({
  onClick,
  type = 'button',
  children,
}: {
  onClick?: () => void
  type?: 'button' | 'submit'
  children: ReactNode
}) {
  return (
    <button
      type={type}
      onClick={onClick}
      className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 transition hover:bg-gray-100 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
    >
      {children}
    </button>
  )
}
