import { useTranslation } from 'react-i18next'
import type { DashboardKpis } from './types'

type Tone = 'ok' | 'warn' | 'critical'

const toneClasses: Record<Tone, string> = {
  ok: 'text-status-ok',
  warn: 'text-status-warn',
  critical: 'text-status-critical',
}

export default function KpiCards({ kpis }: { kpis: DashboardKpis }) {
  const { t } = useTranslation()

  const cards: { key: string; label: string; value: string; trend?: string; tone?: Tone }[] = [
    { key: 'totalOrders', label: t('dashboard.kpi.totalOrders'), value: String(kpis.totalOrders) },
    {
      key: 'pendingOrders',
      label: t('dashboard.kpi.pendingOrders'),
      value: String(kpis.pendingOrders),
      tone: kpis.pendingOrders > 0 ? 'warn' : 'ok',
    },
    {
      key: 'activeShipments',
      label: t('dashboard.kpi.activeShipments'),
      value: String(kpis.activeShipments),
    },
    { key: 'stockOnHand', label: t('dashboard.kpi.stockOnHand'), value: String(kpis.totalStockQuantity) },
    {
      key: 'lowStockAlerts',
      label: t('dashboard.kpi.lowStockAlerts'),
      value: String(kpis.lowStockCount),
      tone: kpis.lowStockCount > 0 ? 'critical' : 'ok',
    },
    {
      key: 'movements24h',
      label: t('dashboard.kpi.movements24h'),
      value: String(kpis.recentMovementsCount),
    },
    {
      key: 'fulfillmentRate',
      label: t('dashboard.kpi.fulfillmentRate'),
      value: `${kpis.fulfillmentRatePercent.toFixed(0)}%`,
      trend: t('dashboard.kpi.target', { percent: kpis.fulfillmentTargetPercent.toFixed(0) }),
      tone: kpis.fulfillmentTargetMet ? 'ok' : 'warn',
    },
  ]

  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
      {cards.map((card) => (
        <div key={card.key} className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900">
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
