import type { DashboardKpis } from './types'

type Tone = 'ok' | 'warn' | 'critical'

const toneClasses: Record<Tone, string> = {
  ok: 'text-status-ok',
  warn: 'text-status-warn',
  critical: 'text-status-critical',
}

export default function KpiCards({ kpis }: { kpis: DashboardKpis }) {
  const cards: { label: string; value: string; trend?: string; tone?: Tone }[] = [
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
      trend: `target ${kpis.fulfillmentTargetPercent.toFixed(0)}%`,
      tone: kpis.fulfillmentTargetMet ? 'ok' : 'warn',
    },
  ]

  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
      {cards.map((card) => (
        <div key={card.label} className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900">
          <div className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
            {card.label}
          </div>
          <div className={`mt-1 text-2xl font-bold ${card.tone ? toneClasses[card.tone] : ''}`}>
            {card.value}
          </div>
          {card.trend && (
            <div className="mt-1 text-xs text-gray-400 dark:text-gray-500">{card.trend}</div>
          )}
        </div>
      ))}
    </div>
  )
}
