import { describe, expect, it } from 'vitest'
import { hasAnyRole } from './roles'

describe('hasAnyRole', () => {
  it('matches when the role is in the allowed list', () => {
    expect(hasAnyRole('ADMIN', ['ADMIN', 'OPERATOR'])).toBe(true)
    expect(hasAnyRole('OPERATOR', ['ADMIN', 'OPERATOR'])).toBe(true)
  })

  it('rejects roles outside the allowed list', () => {
    expect(hasAnyRole('AUDITOR', ['ADMIN', 'OPERATOR'])).toBe(false)
  })

  it('rejects a missing role', () => {
    expect(hasAnyRole(null, ['ADMIN'])).toBe(false)
    expect(hasAnyRole(undefined, ['ADMIN'])).toBe(false)
  })
})
