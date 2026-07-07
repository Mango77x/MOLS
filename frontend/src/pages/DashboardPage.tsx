import { useEffect, useState } from 'react'
import { api } from '../api/client'

interface DashboardKpis {
  totalOrders: number
  pendingOrders: number
  activeShipments: number
  totalStockQuantity: number
  lowStockCount: number
  recentMovementsCount: number
  fulfillmentRatePercent: number
  fulfillmentTargetMet: boolean
}

interface DashboardData {
  kpis: DashboardKpis
}

/**
 * Dashboard landing page. Sprint 1 renders the KPI row from
 * GET /api/dashboard; charts, map and alerts arrive in Sprints 2–3.
 */
export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    api
      .get<DashboardData>('/dashboard')
      .then((response) => {
        if (!cancelled) setData(response.data)
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (error) {
    return (
      <p className="text-sm text-status-critical">
        The dashboard could not be loaded. Please try again.
      </p>
    )
  }

  if (!data) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading dashboard…</p>
  }

  const { kpis } = data
  const cards: { label: string; value: string; tone?: 'ok' | 'warn' | 'critical' }[] = [
    { label: 'Total orders', value: String(kpis.totalOrders) },
    {
      label: 'Pending orders',
      value: String(kpis.pendingOrders),
      tone: kpis.pendingOrders > 0 ? 'warn' : 'ok',
    },
    { label: 'Active shipments', value: String(kpis.activeShipments) },
    { label: 'Stock on hand', value: String(kpis.totalStockQuantity) },
    {
      label: 'Low stock alerts',
      value: String(kpis.lowStockCount),
      tone: kpis.lowStockCount > 0 ? 'critical' : 'ok',
    },
    { label: 'Movements (24h)', value: String(kpis.recentMovementsCount) },
    {
      label: 'Fulfillment rate',
      value: `${kpis.fulfillmentRatePercent.toFixed(0)}%`,
      tone: kpis.fulfillmentTargetMet ? 'ok' : 'warn',
    },
  ]

  const toneClasses = {
    ok: 'text-status-ok',
    warn: 'text-status-warn',
    critical: 'text-status-critical',
  } as const

  return (
    <div>
      <h1 className="mb-4 text-xl font-bold">Dashboard</h1>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
        {cards.map((card) => (
          <div
            key={card.label}
            className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900"
          >
            <div className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
              {card.label}
            </div>
            <div
              className={`mt-1 text-2xl font-bold ${card.tone ? toneClasses[card.tone] : ''}`}
            >
              {card.value}
            </div>
          </div>
        ))}
      </div>
      <p className="mt-6 text-sm text-gray-500 dark:text-gray-400">
        Charts, the logistics map and actionable alerts land here in the next sprints.
      </p>
    </div>
  )
}
