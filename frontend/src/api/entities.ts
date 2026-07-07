/** Mirrors the REST API's response DTOs (src/main/java/com/mls/logistics/dto/response/). */

export interface WarehouseEntity {
  id: number
  name: string
  location: string | null
  latitude: number | null
  longitude: number | null
}

export interface ResourceEntity {
  id: number
  name: string
  type: string
  criticality: string
}

export type VehicleStatus = 'AVAILABLE' | 'IN_USE' | 'IN_REPAIR'

export interface VehicleEntity {
  id: number
  type: string
  capacity: number
  status: VehicleStatus
}

export interface UnitEntity {
  id: number
  name: string
  location: string | null
  latitude: number | null
  longitude: number | null
}

export interface StockEntity {
  id: number
  resourceId: number
  warehouseId: number
  quantity: number
}

export type MovementType = 'ENTRY' | 'EXIT'

export interface MovementEntity {
  id: number
  stockId: number
  type: MovementType
  quantity: number
  dateTime: string
  orderId: number | null
  shipmentId: number | null
  reason: string | null
  createdBy: string | null
}

export type OrderStatus = 'CREATED' | 'VALIDATED' | 'COMPLETED' | 'CANCELLED'

export interface OrderEntity {
  id: number
  unitId: number
  dateCreated: string
  status: OrderStatus
}

export interface OrderItemEntity {
  id: number
  orderId: number
  resourceId: number
  quantity: number
}

export type ShipmentStatus = 'PLANNED' | 'IN_TRANSIT' | 'DELIVERED'

export interface ShipmentEntity {
  id: number
  orderId: number
  vehicleId: number
  warehouseId: number
  status: ShipmentStatus
}
