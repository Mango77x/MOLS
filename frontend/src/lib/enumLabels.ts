/**
 * Human-readable labels for the app's status/type enums.
 *
 * Before this existed, each page/form hardcoded its own copy of these
 * labels — list-page filter dropdowns and status badges ended up showing
 * the raw enum value (e.g. "PARTIALLY_SHIPPED") while forms showed a
 * friendly one ("Partially shipped"), and the two copies drifted (e.g. the
 * order wizard's vehicle picker missing the id that the shipment edit
 * page's otherwise-identical picker included). One source of truth here
 * fixes the whole class of inconsistency instead of one call site at a time.
 */

export const ORDER_STATUS_LABELS: Record<string, string> = {
  CREATED: 'Created',
  VALIDATED: 'Validated',
  PARTIALLY_SHIPPED: 'Partially shipped',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

export const SHIPMENT_STATUS_LABELS: Record<string, string> = {
  PLANNED: 'Planned',
  IN_TRANSIT: 'In transit',
  DELIVERED: 'Delivered',
}

export const VEHICLE_STATUS_LABELS: Record<string, string> = {
  AVAILABLE: 'Available',
  IN_USE: 'In use',
  IN_REPAIR: 'In repair',
}

export const VEHICLE_TYPE_LABELS: Record<string, string> = {
  LAND: 'Land',
  SEA: 'Sea',
  AIR: 'Air',
}

/** Human-readable label for an enum value; falls back to the raw value if unmapped. */
export function enumLabel(map: Record<string, string>, value: string): string {
  return map[value] ?? value
}
