import { useTranslation } from 'react-i18next'
import type { ResourceEntity, StockEntity, WarehouseEntity } from '../../api/entities'
import { useLookup } from '../../api/lookups'
import type { RecentMovement } from './types'

// Locale passed explicitly (the active i18next language, not `undefined`)
// so the date format always matches the UI language the user picked,
// instead of silently following whatever the browser happens to report.
function formatDateTime(value: string, locale: string) {
  return new Date(value).toLocaleString(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

const TYPE_BADGE: Record<string, string> = {
  ENTRY: 'bg-status-ok/10 text-status-ok',
  EXIT: 'bg-status-warn/10 text-status-warn',
}

export default function RecentActivity({ movements }: { movements: RecentMovement[] }) {
  const { t, i18n } = useTranslation()
  const { byId: stocks } = useLookup<StockEntity>('/stocks')
  const { byId: resources } = useLookup<ResourceEntity>('/resources')
  const { byId: warehouses } = useLookup<WarehouseEntity>('/warehouses')

  // Same resolution MovementsPage.tsx uses: a movement only carries a
  // stockId, so its resource/warehouse names come from looking up that
  // stock row first, then its resource/warehouse.
  function resourceName(stockId: number) {
    const stock = stocks[stockId]
    if (!stock) return `stock #${stockId}`
    return resources[stock.resourceId]?.name ?? `resource #${stock.resourceId}`
  }

  function warehouseName(stockId: number) {
    const stock = stocks[stockId]
    if (!stock) return '—'
    return warehouses[stock.warehouseId]?.name ?? `warehouse #${stock.warehouseId}`
  }

  return (
    <div className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900">
      <h2 className="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-200">
        {t('dashboard.recentActivity.title')}
      </h2>
      {movements.length === 0 ? (
        <p className="text-sm text-gray-400 dark:text-gray-500">{t('dashboard.recentActivity.empty')}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
                <th className="pb-2 pr-4 font-medium">{t('dashboard.recentActivity.time')}</th>
                <th className="pb-2 pr-4 font-medium">{t('dashboard.recentActivity.type')}</th>
                <th className="pb-2 pr-4 font-medium">{t('dashboard.recentActivity.resource')}</th>
                <th className="pb-2 pr-4 font-medium">{t('dashboard.recentActivity.warehouse')}</th>
                <th className="pb-2 pr-4 font-medium">{t('dashboard.recentActivity.quantity')}</th>
                <th className="pb-2 font-medium">{t('dashboard.recentActivity.reason')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {movements.map((movement) => (
                <tr key={movement.id}>
                  <td className="py-2 pr-4 whitespace-nowrap text-gray-600 dark:text-gray-300">
                    {formatDateTime(movement.dateTime, i18n.language)}
                  </td>
                  <td className="py-2 pr-4">
                    <span
                      className={`rounded px-2 py-0.5 text-xs font-medium ${TYPE_BADGE[movement.type] ?? 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300'}`}
                    >
                      {movement.type}
                    </span>
                  </td>
                  <td className="py-2 pr-4 text-gray-600 dark:text-gray-300">
                    {resourceName(movement.stockId)}
                  </td>
                  <td className="py-2 pr-4 text-gray-600 dark:text-gray-300">
                    {warehouseName(movement.stockId)}
                  </td>
                  <td className="py-2 pr-4 font-medium">{movement.quantity}</td>
                  <td className="py-2 text-gray-500 dark:text-gray-400">
                    {movement.reason ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
