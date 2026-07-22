import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import type { DashboardAlerts, DashboardThresholds } from './types'

function staleTone(daysPending: number, staleOrderDays: number) {
  if (daysPending > staleOrderDays * 2) return 'text-status-critical'
  return 'text-status-warn'
}

export default function AlertsPanel({
  alerts,
  thresholds,
}: {
  alerts: DashboardAlerts
  thresholds: DashboardThresholds
}) {
  const { t } = useTranslation()

  return (
    <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
      <div className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900">
        <h2 className="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-200">
          {t('dashboard.alerts.lowStockTitle')}
        </h2>
        {alerts.lowStock.length === 0 ? (
          <p className="text-sm text-gray-400 dark:text-gray-500">{t('dashboard.alerts.noLowStock')}</p>
        ) : (
          <ul className="divide-y divide-gray-100 dark:divide-gray-800">
            {alerts.lowStock.map((item) => (
              <li key={item.stockId} className="flex items-center justify-between gap-3 py-2">
                <div>
                  <div className="text-sm font-medium">{item.resourceName}</div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    {item.warehouseName}
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span
                    className={`text-sm font-bold ${item.critical ? 'text-status-critical' : 'text-status-warn'}`}
                  >
                    {item.quantity}
                  </span>
                  <Link
                    to={`/stocks/${item.stockId}/adjust`}
                    className="text-xs font-medium text-army-700 underline dark:text-army-300"
                  >
                    {t('dashboard.alerts.adjust')}
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900">
        <h2 className="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-200">
          {t('dashboard.alerts.pendingOrdersTitle', { days: thresholds.staleOrderDays })}
        </h2>
        {alerts.staleOrders.length === 0 ? (
          <p className="text-sm text-gray-400 dark:text-gray-500">{t('dashboard.alerts.noStaleOrders')}</p>
        ) : (
          <ul className="divide-y divide-gray-100 dark:divide-gray-800">
            {alerts.staleOrders.map((order) => (
              <li key={order.orderId} className="flex items-center justify-between gap-3 py-2">
                <div>
                  <div className="text-sm font-medium">
                    {t('dashboard.alerts.orderNumber', { id: order.orderId })}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">{order.unitName}</div>
                </div>
                <span
                  className={`text-sm font-bold ${staleTone(order.daysPending, thresholds.staleOrderDays)}`}
                >
                  {order.daysPending}d
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
