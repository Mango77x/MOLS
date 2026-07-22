import type { Role } from '../auth/store'

export interface NavItem {
  /** i18next key (under `nav.*`) — resolved with `t()` at render time. */
  labelKey: string
  to: string
  /** Only shown to (and reachable by) ADMIN users. */
  adminOnly?: boolean
}

/** Main navigation, mirroring the sections of the Thymeleaf UI. */
export const NAV_ITEMS: NavItem[] = [
  { labelKey: 'nav.dashboard', to: '/' },
  { labelKey: 'nav.warehouses', to: '/warehouses' },
  { labelKey: 'nav.resources', to: '/resources' },
  { labelKey: 'nav.vehicles', to: '/vehicles' },
  { labelKey: 'nav.stock', to: '/stocks' },
  { labelKey: 'nav.orders', to: '/orders' },
  { labelKey: 'nav.shipments', to: '/shipments' },
  { labelKey: 'nav.units', to: '/units' },
  { labelKey: 'nav.movements', to: '/movements' },
  { labelKey: 'nav.users', to: '/users', adminOnly: true },
]

/** Navigation entries visible for the given role. */
export function visibleNavItems(role: Role): NavItem[] {
  return NAV_ITEMS.filter((item) => !item.adminOnly || role === 'ADMIN')
}
