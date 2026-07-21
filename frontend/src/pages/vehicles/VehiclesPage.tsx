import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCurrentRole } from '../../auth/roles'
import { api } from '../../api/client'
import type { VehicleEntity, VehicleStatus } from '../../api/entities'
import Badge, { type BadgeTone } from '../../components/Badge'
import DataTable from '../../components/table/DataTable'
import { DeleteAction, RouteActionLink } from '../../components/table/RowActions'
import { useServerTable } from '../../components/table/useServerTable'
import { enumLabel, VEHICLE_STATUS_LABELS, VEHICLE_TYPE_LABELS } from '../../lib/enumLabels'

const STATUS_TONE: Record<VehicleStatus, BadgeTone> = {
  AVAILABLE: 'ok',
  IN_USE: 'warn',
  IN_REPAIR: 'critical',
}

export default function VehiclesPage() {
  const role = useCurrentRole()
  const isAdmin = role === 'ADMIN'
  const [type, setType] = useState('')
  const [status, setStatus] = useState('')

  const table = useServerTable<VehicleEntity>('/vehicles', { type, status }, { field: 'id', desc: false })

  async function handleDelete(id: number) {
    await api.delete(`/vehicles/${id}`)
    table.reload()
  }

  const columns: ColumnDef<VehicleEntity, unknown>[] = [
    { id: 'id', header: 'ID', accessorKey: 'id', enableSorting: true },
    {
      id: 'type',
      header: 'Type',
      accessorKey: 'type',
      enableSorting: true,
      cell: ({ getValue }) => enumLabel(VEHICLE_TYPE_LABELS, getValue<string>()),
    },
    {
      id: 'capacity',
      header: 'Capacity',
      accessorKey: 'capacity',
      enableSorting: true,
      cell: ({ getValue }) => `${getValue<number>()} kg`,
    },
    {
      id: 'status',
      header: 'Status',
      accessorKey: 'status',
      enableSorting: true,
      cell: ({ getValue }) => {
        const value = getValue<VehicleStatus>()
        return <Badge tone={STATUS_TONE[value] ?? 'neutral'}>{enumLabel(VEHICLE_STATUS_LABELS, value)}</Badge>
      },
    },
  ]
  if (isAdmin) {
    columns.push({
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => (
        <div className="flex gap-3">
          <RouteActionLink to={`/vehicles/${row.original.id}/edit`}>Edit</RouteActionLink>
          <DeleteAction label="vehicle" onConfirm={() => handleDelete(row.original.id)} />
        </div>
      ),
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">Vehicles</h1>
        {isAdmin && (
          <Link
            to="/vehicles/new"
            className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
          >
            New vehicle
          </Link>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <input
          value={type}
          onChange={(e) => setType(e.target.value)}
          placeholder="Filter by type…"
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="">All statuses</option>
          {Object.entries(VEHICLE_STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage="No vehicles to display"
        sort={table.sort}
        onSortChange={table.toggleSort}
        page={table.page}
        size={table.size}
        totalPages={table.totalPages}
        totalElements={table.totalElements}
        onPageChange={table.setPage}
        onSizeChange={table.setSize}
      />
    </div>
  )
}
