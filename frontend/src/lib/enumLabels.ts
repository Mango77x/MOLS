import i18n from '../i18n'

/**
 * Human-readable labels for the app's status/type enums.
 *
 * Before the shared-label module existed, each page/form hardcoded its own
 * copy of these labels — list-page filter dropdowns and status badges ended
 * up showing the raw enum value (e.g. "PARTIALLY_SHIPPED") while forms
 * showed a friendly one ("Partially shipped"), and the two copies drifted.
 * One source of truth here fixes the whole class of inconsistency instead
 * of one call site at a time.
 *
 * The maps hold i18next keys rather than literal text — `enumLabel()`
 * resolves through the global `i18n` instance directly (not the
 * `useTranslation()` hook) so it stays a plain function callable from
 * non-component code (column defs, sort comparators) as well as JSX.
 */

export const ORDER_STATUS_LABELS: Record<string, string> = {
  CREATED: 'enums.orderStatus.CREATED',
  VALIDATED: 'enums.orderStatus.VALIDATED',
  PARTIALLY_SHIPPED: 'enums.orderStatus.PARTIALLY_SHIPPED',
  COMPLETED: 'enums.orderStatus.COMPLETED',
  CANCELLED: 'enums.orderStatus.CANCELLED',
}

export const SHIPMENT_STATUS_LABELS: Record<string, string> = {
  PLANNED: 'enums.shipmentStatus.PLANNED',
  IN_TRANSIT: 'enums.shipmentStatus.IN_TRANSIT',
  DELIVERED: 'enums.shipmentStatus.DELIVERED',
}

export const VEHICLE_STATUS_LABELS: Record<string, string> = {
  AVAILABLE: 'enums.vehicleStatus.AVAILABLE',
  IN_USE: 'enums.vehicleStatus.IN_USE',
  IN_REPAIR: 'enums.vehicleStatus.IN_REPAIR',
}

export const VEHICLE_TYPE_LABELS: Record<string, string> = {
  LAND: 'enums.vehicleType.LAND',
  SEA: 'enums.vehicleType.SEA',
  AIR: 'enums.vehicleType.AIR',
}

export const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'enums.role.ADMIN',
  OPERATOR: 'enums.role.OPERATOR',
  AUDITOR: 'enums.role.AUDITOR',
}

/** Human-readable label for an enum value; falls back to the raw value if unmapped. */
export function enumLabel(map: Record<string, string>, value: string): string {
  const key = map[value]
  return key ? i18n.t(key) : value
}
