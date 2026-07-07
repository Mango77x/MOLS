/** Mirrors com.mls.logistics.dto.response.MapResponse returned by GET /api/map. */
export type StockStatus = 'OK' | 'WARNING' | 'CRITICAL'

export interface WarehousePin {
  id: number
  name: string
  latitude: number
  longitude: number
  stockStatus: StockStatus
}

export interface UnitPin {
  id: number
  name: string
  latitude: number
  longitude: number
}

export interface RoutePoint {
  id: number
  name: string
  latitude: number
  longitude: number
}

export type ShipmentStatus = 'PLANNED' | 'IN_TRANSIT' | 'DELIVERED'

export interface ShipmentRoute {
  id: number
  status: ShipmentStatus
  origin: RoutePoint
  destination: RoutePoint
}

export interface MapData {
  warehouses: WarehousePin[]
  units: UnitPin[]
  shipments: ShipmentRoute[]
}
