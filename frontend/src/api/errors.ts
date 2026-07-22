import type { UseFormSetError } from 'react-hook-form'
import i18n from '../i18n'

interface ApiErrorBody {
  message?: string
  fieldErrors?: Record<string, string>
  /** Machine-readable identifier under the `errors.*` translations; absent for anything not yet migrated. */
  code?: string
  /** Values to interpolate into the translated message; absent when `code` is absent or needs none. */
  params?: Record<string, unknown>
}

export interface ApiError {
  message: string
  fieldErrors?: Record<string, string>
}

/**
 * Normalizes an Axios error from the API into a friendly message plus, for
 * 400 validation failures, a per-field error map (`GlobalExceptionHandler`'s
 * `fieldErrors` shape). Business conflicts (404/409, `ErrorResponse.message`)
 * surface as a single form-level message instead.
 *
 * When the backend attaches a `code` (+ `params`) and it's a recognized key
 * under `errors.*`, that's translated and shown instead of `message` — the
 * backend's `message` is always English (a fallback for logs/Swagger/older
 * clients), so this is what actually localizes API error text. Not every
 * exception carries a code yet; `message` is shown as-is for the rest.
 */
export function extractApiError(error: unknown): ApiError {
  const data = (error as { response?: { data?: ApiErrorBody } } | undefined)?.response?.data
  if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
    return { message: data.message ?? 'Please fix the highlighted fields.', fieldErrors: data.fieldErrors }
  }
  if (data?.code && i18n.exists(`errors.${data.code}`)) {
    return { message: i18n.t(`errors.${data.code}`, data.params) }
  }
  if (data?.message) {
    return { message: data.message }
  }
  return { message: 'Something went wrong. Please try again.' }
}

/**
 * Applies an API error to a react-hook-form instance: field-level messages
 * via `setError`, returning the remaining form-level message (if any) to
 * show in a banner.
 */
export function applyApiError<T extends Record<string, unknown>>(
  apiError: ApiError,
  setError: UseFormSetError<T>,
): string | null {
  if (apiError.fieldErrors) {
    for (const [field, message] of Object.entries(apiError.fieldErrors)) {
      setError(field as never, { type: 'server', message })
    }
    return null
  }
  return apiError.message
}
