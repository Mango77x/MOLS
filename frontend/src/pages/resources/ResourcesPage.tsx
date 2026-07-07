import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCurrentRole } from '../../auth/roles'
import { api } from '../../api/client'
import type { ResourceEntity } from '../../api/entities'
import Badge, { type BadgeTone } from '../../components/Badge'
import DataTable from '../../components/table/DataTable'
import { DeleteAction, RouteActionLink } from '../../components/table/RowActions'
import { useServerTable } from '../../components/table/useServerTable'

const CRITICALITY_TONE: Record<string, BadgeTone> = {
  HIGH: 'critical',
  MEDIUM: 'warn',
  LOW: 'ok',
}

export default function ResourcesPage() {
  const role = useCurrentRole()
  const isAdmin = role === 'ADMIN'
  const [name, setName] = useState('')
  const [type, setType] = useState('')

  const table = useServerTable<ResourceEntity>('/resources', { name, type }, { field: 'id', desc: false })

  async function handleDelete(id: number) {
    await api.delete(`/resources/${id}`)
    table.reload()
  }

  const columns: ColumnDef<ResourceEntity, unknown>[] = [
    { id: 'id', header: 'ID', accessorKey: 'id', enableSorting: true },
    { id: 'name', header: 'Name', accessorKey: 'name', enableSorting: true },
    { id: 'type', header: 'Type', accessorKey: 'type', enableSorting: true },
    {
      id: 'criticality',
      header: 'Criticality',
      accessorKey: 'criticality',
      enableSorting: true,
      cell: ({ getValue }) => {
        const value = getValue<string>()
        return <Badge tone={CRITICALITY_TONE[value] ?? 'neutral'}>{value}</Badge>
      },
    },
  ]
  if (isAdmin) {
    columns.push({
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => (
        <div className="flex gap-3">
          <RouteActionLink to={`/resources/${row.original.id}/edit`}>Edit</RouteActionLink>
          <DeleteAction label="resource" onConfirm={() => handleDelete(row.original.id)} />
        </div>
      ),
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">Resources</h1>
        {isAdmin && (
          <Link
            to="/resources/new"
            className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
          >
            New resource
          </Link>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Search by name…"
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
        <input
          value={type}
          onChange={(e) => setType(e.target.value)}
          placeholder="Filter by type…"
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
      </div>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage="No resources to display"
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
