import type { Role } from '../auth/store'

export interface NavItem {
  label: string
  to: string
  /** Only shown to (and reachable by) ADMIN users. */
  adminOnly?: boolean
}

/** Main navigation, mirroring the sections of the Thymeleaf UI. */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', to: '/' },
  { label: 'Warehouses', to: '/warehouses' },
  { label: 'Resources', to: '/resources' },
  { label: 'Vehicles', to: '/vehicles' },
  { label: 'Stock', to: '/stocks' },
  { label: 'Orders', to: '/orders' },
  { label: 'Shipments', to: '/shipments' },
  { label: 'Units', to: '/units' },
  { label: 'Audit log', to: '/movements' },
  { label: 'Users', to: '/users', adminOnly: true },
]

/** Navigation entries visible for the given role. */
export function visibleNavItems(role: Role): NavItem[] {
  return NAV_ITEMS.filter((item) => !item.adminOnly || role === 'ADMIN')
}
