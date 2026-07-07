import L from 'leaflet'
import type { StockStatus } from './mapTypes'

const STOCK_STATUS_CLASS: Record<StockStatus, string> = {
  OK: 'bg-status-ok',
  WARNING: 'bg-status-warn',
  CRITICAL: 'bg-status-critical',
}

/** Circular pin colored by warehouse stock status. */
export function warehouseIcon(status: StockStatus, selected: boolean) {
  const ring = selected ? 'ring-2 ring-offset-1 ring-army-700' : ''
  return L.divIcon({
    className: '',
    html: `<div class="h-4 w-4 rounded-full border-2 border-white shadow ${STOCK_STATUS_CLASS[status]} ${ring}"></div>`,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  })
}

/** Diamond pin (visually distinct from warehouses) for units. */
export function unitIcon(selected: boolean) {
  const ring = selected ? 'ring-2 ring-offset-1 ring-army-700' : ''
  return L.divIcon({
    className: '',
    html: `<div class="h-3 w-3 rotate-45 border-2 border-white bg-army-600 shadow ${ring}"></div>`,
    iconSize: [14, 14],
    iconAnchor: [7, 7],
  })
}
