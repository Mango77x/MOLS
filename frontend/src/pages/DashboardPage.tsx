import { useEffect, useState } from 'react'
import { api } from '../api/client'
import AlertsPanel from './dashboard/AlertsPanel'
import Charts from './dashboard/Charts'
import KpiCards from './dashboard/KpiCards'
import LogisticsMap from './dashboard/LogisticsMap'
import RecentActivity from './dashboard/RecentActivity'
import type { DashboardData } from './dashboard/types'

/**
 * Dashboard landing page: KPI cards, charts and actionable alerts from
 * GET /api/dashboard. The logistics map lands in Sprint 3.
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

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Dashboard</h1>
      <LogisticsMap />
      <KpiCards kpis={data.kpis} />
      <Charts charts={data.charts} />
      <AlertsPanel alerts={data.alerts} thresholds={data.thresholds} />
      <RecentActivity movements={data.recentMovements} />
    </div>
  )
}
