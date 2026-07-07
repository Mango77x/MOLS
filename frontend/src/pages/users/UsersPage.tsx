import type { ColumnDef } from '@tanstack/react-table'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../api/client'
import type { UserEntity, UserRole } from '../../api/entities'
import { extractApiError } from '../../api/errors'
import Badge from '../../components/Badge'
import { FormBanner } from '../../components/form/fields'
import DataTable from '../../components/table/DataTable'
import { RouteActionLink } from '../../components/table/RowActions'

export default function UsersPage() {
  const [users, setUsers] = useState<UserEntity[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [banner, setBanner] = useState<string | null>(null)

  const loadUsers = useCallback(async () => {
    setLoading(true)
    setError(false)
    try {
      const response = await api.get<UserEntity[]>('/users')
      setUsers(response.data)
    } catch {
      setError(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadUsers()
  }, [loadUsers])

  async function handleRoleChange(user: UserEntity, role: UserRole) {
    if (role === user.role) return
    if (!window.confirm(`Change ${user.username}'s role to ${role}?`)) return
    setBanner(null)
    try {
      await api.patch(`/users/${user.id}/role`, { role })
      await loadUsers()
    } catch (err) {
      setBanner(extractApiError(err).message)
    }
  }

  async function handleToggleEnabled(user: UserEntity) {
    const action = user.enabled ? 'Disable' : 'Enable'
    if (!window.confirm(`${action} account '${user.username}'?`)) return
    setBanner(null)
    try {
      await api.patch(`/users/${user.id}/enabled`, { enabled: !user.enabled })
      await loadUsers()
    } catch (err) {
      setBanner(extractApiError(err).message)
    }
  }

  const columns: ColumnDef<UserEntity, unknown>[] = [
    { id: 'id', header: 'ID', accessorKey: 'id' },
    { id: 'username', header: 'Username', accessorKey: 'username' },
    {
      id: 'role',
      header: 'Role',
      cell: ({ row }) => (
        <select
          value={row.original.role}
          onChange={(e) => handleRoleChange(row.original, e.target.value as UserRole)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          <option value="ADMIN">ADMIN</option>
          <option value="OPERATOR">OPERATOR</option>
          <option value="AUDITOR">AUDITOR</option>
        </select>
      ),
    },
    {
      id: 'enabled',
      header: 'Status',
      cell: ({ row }) => (
        <Badge tone={row.original.enabled ? 'ok' : 'neutral'}>
          {row.original.enabled ? 'Enabled' : 'Disabled'}
        </Badge>
      ),
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }) => (
        <div className="flex gap-3">
          <RouteActionLink to={`/users/${row.original.id}/reset-password`}>
            Reset password
          </RouteActionLink>
          <button
            type="button"
            onClick={() => handleToggleEnabled(row.original)}
            className={row.original.enabled ? 'text-status-critical underline' : 'text-status-ok underline'}
          >
            {row.original.enabled ? 'Disable' : 'Enable'}
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">Users</h1>
        <Link
          to="/users/new"
          className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
        >
          New user
        </Link>
      </div>

      <FormBanner message={banner} />

      <DataTable
        columns={columns}
        data={users}
        loading={loading}
        error={error}
        emptyMessage="No users to display"
        page={0}
        size={100}
        totalPages={1}
        totalElements={users.length}
        onPageChange={() => {}}
        onSizeChange={() => {}}
      />
    </div>
  )
}
