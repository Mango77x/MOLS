import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Label,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { ChartSeries, DashboardCharts } from './types'

const ARMY_GREEN = '#5a6b38'
const STATUS_OK = '#16a34a'
const STATUS_WARN = '#ca8a04'
const STATUS_CRITICAL = '#dc2626'

function toRows(series: ChartSeries) {
  return series.labels.map((label, index) => ({ label, value: series.values[index] ?? 0 }))
}

function hasData(series: ChartSeries) {
  return series.values.some((value) => value > 0)
}

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900">
      <h2 className="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-200">{title}</h2>
      {children}
    </div>
  )
}

function EmptyState() {
  const { t } = useTranslation()
  return (
    <p className="flex h-56 items-center justify-center text-sm text-gray-400 dark:text-gray-500">
      {t('dashboard.charts.noData')}
    </p>
  )
}

function StockByWarehouseChart({ series }: { series: ChartSeries }) {
  if (!hasData(series)) return <EmptyState />
  const rows = toRows(series)
  return (
    <ResponsiveContainer width="100%" height={224}>
      <BarChart data={rows} layout="vertical" margin={{ left: 8, right: 16 }}>
        <CartesianGrid strokeDasharray="3 3" horizontal={false} />
        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} />
        <YAxis type="category" dataKey="label" width={100} tick={{ fontSize: 12 }} />
        <Tooltip />
        <Bar dataKey="value" fill={ARMY_GREEN} radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}

const STATUS_COLORS: Record<string, string> = {
  ENTRY: STATUS_OK,
  EXIT: STATUS_WARN,
  PENDING: STATUS_WARN,
  COMPLETED: STATUS_OK,
  CANCELLED: STATUS_CRITICAL,
}
const FALLBACK_COLORS = [ARMY_GREEN, STATUS_WARN, STATUS_CRITICAL, STATUS_OK]

// NOTE: row.label here is the raw series label the backend sends (movement
// type / order status grouping), not translated — the dashboard's chart
// series contracts need their own look before extending enumLabels.ts to
// cover them, tracked as Sprint 17 scope, not this sprint's shared-chrome pass.
function DonutLegend({ series }: { series: ChartSeries }) {
  const rows = toRows(series)
  return (
    <ul className="mt-2 flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs text-gray-600 dark:text-gray-300">
      {rows.map((row, index) => (
        <li key={row.label} className="flex items-center gap-1.5">
          <span
            aria-hidden="true"
            className="h-2.5 w-2.5 shrink-0 rounded-full"
            style={{
              backgroundColor: STATUS_COLORS[row.label] ?? FALLBACK_COLORS[index % FALLBACK_COLORS.length],
            }}
          />
          <span>
            {row.label} ({row.value})
          </span>
        </li>
      ))}
    </ul>
  )
}

function DonutChart({ series }: { series: ChartSeries }) {
  const { t } = useTranslation()
  if (!hasData(series)) return <EmptyState />
  const rows = toRows(series)
  const total = rows.reduce((sum, row) => sum + row.value, 0)
  return (
    <div>
      <ResponsiveContainer width="100%" height={224}>
        <PieChart>
          <Pie
            data={rows}
            dataKey="value"
            nameKey="label"
            innerRadius={50}
            outerRadius={80}
            paddingAngle={2}
            isAnimationActive={false}
          >
            {rows.map((row, index) => (
              <Cell
                key={row.label}
                fill={STATUS_COLORS[row.label] ?? FALLBACK_COLORS[index % FALLBACK_COLORS.length]}
              />
            ))}
            <Label
              value={t('dashboard.charts.total', { value: total })}
              position="center"
              className="fill-gray-500 text-xs dark:fill-gray-400"
            />
          </Pie>
          <Tooltip />
        </PieChart>
      </ResponsiveContainer>
      <DonutLegend series={series} />
    </div>
  )
}

export default function Charts({ charts }: { charts: DashboardCharts }) {
  const { t } = useTranslation()
  return (
    <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
      <ChartCard title={t('dashboard.charts.stockByWarehouse')}>
        <StockByWarehouseChart series={charts.stockByWarehouse} />
      </ChartCard>
      <ChartCard title={t('dashboard.charts.movementsByType')}>
        <DonutChart series={charts.movementsByType} />
      </ChartCard>
      <ChartCard title={t('dashboard.charts.ordersByStatus')}>
        <DonutChart series={charts.ordersByStatus} />
      </ChartCard>
    </div>
  )
}
