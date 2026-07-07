import type { RecentMovement } from './types'

function formatDateTime(value: string) {
  return new Date(value).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

const TYPE_BADGE: Record<string, string> = {
  ENTRY: 'bg-status-ok/10 text-status-ok',
  EXIT: 'bg-status-warn/10 text-status-warn',
}

export default function RecentActivity({ movements }: { movements: RecentMovement[] }) {
  return (
    <div className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900">
      <h2 className="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-200">
        Recent activity
      </h2>
      {movements.length === 0 ? (
        <p className="text-sm text-gray-400 dark:text-gray-500">No recent activity to display</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
                <th className="pb-2 pr-4 font-medium">Time</th>
                <th className="pb-2 pr-4 font-medium">Type</th>
                <th className="pb-2 pr-4 font-medium">Stock</th>
                <th className="pb-2 pr-4 font-medium">Quantity</th>
                <th className="pb-2 font-medium">Reason</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {movements.map((movement) => (
                <tr key={movement.id}>
                  <td className="py-2 pr-4 whitespace-nowrap text-gray-600 dark:text-gray-300">
                    {formatDateTime(movement.dateTime)}
                  </td>
                  <td className="py-2 pr-4">
                    <span
                      className={`rounded px-2 py-0.5 text-xs font-medium ${TYPE_BADGE[movement.type] ?? 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300'}`}
                    >
                      {movement.type}
                    </span>
                  </td>
                  <td className="py-2 pr-4 text-gray-600 dark:text-gray-300">
                    #{movement.stockId}
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
