/** Mirrors com.mls.logistics.dto.response.DashboardResponse returned by GET /api/dashboard. */
export interface DashboardKpis {
  totalOrders: number
  pendingOrders: number
  completedOrders: number
  cancelledOrders: number
  activeShipments: number
  totalStockQuantity: number
  stockWarehouseCount: number
  lowStockCount: number
  recentMovementsCount: number
  fulfillmentRatePercent: number
  fulfillmentTargetPercent: number
  fulfillmentTargetMet: boolean
}

export interface ChartSeries {
  labels: string[]
  values: number[]
}

export interface DashboardCharts {
  stockByWarehouse: ChartSeries
  movementsByType: ChartSeries
  ordersByStatus: ChartSeries
}

export interface LowStockAlert {
  stockId: number
  resourceName: string
  warehouseName: string
  quantity: number
  critical: boolean
}

export interface StaleOrderAlert {
  orderId: number
  unitName: string
  daysPending: number
}

export interface DashboardAlerts {
  lowStock: LowStockAlert[]
  staleOrders: StaleOrderAlert[]
}

export interface RecentMovement {
  id: number
  stockId: number
  type: string
  quantity: number
  dateTime: string
  orderId: number | null
  shipmentId: number | null
  reason: string | null
  createdBy: string | null
}

export interface DashboardThresholds {
  lowStockThreshold: number
  criticalStockThreshold: number
  staleOrderDays: number
  recentActivityHours: number
  movementChartDays: number
}

export interface DashboardData {
  kpis: DashboardKpis
  charts: DashboardCharts
  alerts: DashboardAlerts
  recentMovements: RecentMovement[]
  thresholds: DashboardThresholds
}
