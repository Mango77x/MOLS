import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../i18n'
import { extractApiError } from './errors'

function apiErrorLike(data: unknown) {
  return { response: { data } }
}

/**
 * Sprint 17: extractApiError gained a `code`/`params` path — when the
 * backend attaches a recognized code, it's translated via `errors.*`
 * instead of showing the backend's (always-English) `message` verbatim.
 * `message` stays as the fallback for the ~50 exception sites not yet
 * migrated to a code, so both paths need coverage, not just the new one.
 */
describe('extractApiError', () => {
  afterEach(() => {
    void i18n.changeLanguage('en')
  })

  it('translates a recognized code with its params, ignoring the English message', () => {
    const error = apiErrorLike({
      message: 'Cannot delete warehouse with existing stock. Warehouse id: 5',
      code: 'WAREHOUSE_DELETE_HAS_STOCK',
      params: { warehouseId: 5 },
    })

    expect(extractApiError(error).message).toBe('Cannot delete warehouse with existing stock. Warehouse id: 5')
  })

  it('translates the same code into the active non-English language', async () => {
    await i18n.changeLanguage('fr')
    const error = apiErrorLike({
      message: 'Cannot delete warehouse with existing stock. Warehouse id: 5',
      code: 'WAREHOUSE_DELETE_HAS_STOCK',
      params: { warehouseId: 5 },
    })

    expect(extractApiError(error).message).toBe(
      'Impossible de supprimer un entrepôt avec du stock existant. Entrepôt id : 5',
    )
  })

  it('falls back to the English message for an unrecognized code', () => {
    const error = apiErrorLike({
      message: 'Some exception not yet migrated to a code.',
      code: 'SOME_FUTURE_CODE_NOT_IN_TRANSLATIONS',
    })

    expect(extractApiError(error).message).toBe('Some exception not yet migrated to a code.')
  })

  it('falls back to the English message when there is no code at all', () => {
    const error = apiErrorLike({ message: 'Cannot delete a COMPLETED order: its stock movements...' })

    expect(extractApiError(error).message).toBe('Cannot delete a COMPLETED order: its stock movements...')
  })

  it('still prioritizes fieldErrors over a code, unchanged from before', () => {
    const error = apiErrorLike({
      message: 'Validation failed',
      code: 'SOME_CODE',
      fieldErrors: { name: 'Name is required' },
    })

    expect(extractApiError(error)).toEqual({
      message: 'Validation failed',
      fieldErrors: { name: 'Name is required' },
    })
  })

  it('falls back to a generic message when there is no response data at all', () => {
    expect(extractApiError(new Error('network error')).message).toBe('Something went wrong. Please try again.')
  })
})
