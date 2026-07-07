import type { UseFormSetError } from 'react-hook-form'

interface ApiErrorBody {
  message?: string
  fieldErrors?: Record<string, string>
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
 */
export function extractApiError(error: unknown): ApiError {
  const data = (error as { response?: { data?: ApiErrorBody } } | undefined)?.response?.data
  if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
    return { message: data.message ?? 'Please fix the highlighted fields.', fieldErrors: data.fieldErrors }
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
