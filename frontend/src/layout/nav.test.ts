import { describe, expect, it } from 'vitest'
import { NAV_ITEMS, visibleNavItems } from './nav'

describe('visibleNavItems', () => {
  it('shows every section to ADMIN', () => {
    expect(visibleNavItems('ADMIN')).toEqual(NAV_ITEMS)
  })

  it('hides admin-only sections from OPERATOR and AUDITOR', () => {
    for (const role of ['OPERATOR', 'AUDITOR'] as const) {
      const items = visibleNavItems(role)
      expect(items.some((item) => item.adminOnly)).toBe(false)
      expect(items.length).toBe(NAV_ITEMS.length - 1)
    }
  })
})
