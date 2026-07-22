import type { ColumnDef } from '@tanstack/react-table'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useCurrentRole } from '../../auth/roles'
import { api } from '../../api/client'
import type { WarehouseEntity } from '../../api/entities'
import DataTable from '../../components/table/DataTable'
import { DeleteAction, RouteActionLink } from '../../components/table/RowActions'
import { useServerTable } from '../../components/table/useServerTable'

export default function WarehousesPage() {
  const { t } = useTranslation()
  const role = useCurrentRole()
  const isAdmin = role === 'ADMIN'
  const [name, setName] = useState('')

  const table = useServerTable<WarehouseEntity>('/warehouses', { name }, { field: 'id', desc: false })

  async function handleDelete(id: number) {
    await api.delete(`/warehouses/${id}`)
    table.reload()
  }

  const columns: ColumnDef<WarehouseEntity, unknown>[] = [
    { id: 'id', header: t('common.id'), accessorKey: 'id', enableSorting: true },
    { id: 'name', header: t('common.name'), accessorKey: 'name', enableSorting: true },
    { id: 'location', header: t('common.location'), accessorFn: (row) => row.location ?? '—' },
  ]
  if (isAdmin) {
    columns.push({
      id: 'actions',
      header: t('common.actions'),
      cell: ({ row }) => (
        <div className="flex gap-3">
          <RouteActionLink to={`/warehouses/${row.original.id}/edit`}>{t('common.edit')}</RouteActionLink>
          <DeleteAction label={t('warehouses.entityName')} onConfirm={() => handleDelete(row.original.id)} />
        </div>
      ),
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">{t('warehouses.title')}</h1>
        {isAdmin && (
          <Link
            to="/warehouses/new"
            className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
          >
            {t('warehouses.newWarehouse')}
          </Link>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t('warehouses.searchPlaceholder')}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
      </div>

      <DataTable
        columns={columns}
        data={table.rows}
        loading={table.loading}
        error={table.error}
        emptyMessage={t('warehouses.emptyMessage')}
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
