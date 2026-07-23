import type { ColumnDef } from '@tanstack/react-table'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { api } from '../../api/client'
import type { UserEntity, UserRole } from '../../api/entities'
import { extractApiError } from '../../api/errors'
import Badge from '../../components/Badge'
import ConfirmDialog from '../../components/ConfirmDialog'
import { FormBanner } from '../../components/form/fields'
import DataTable from '../../components/table/DataTable'
import { RouteActionLink } from '../../components/table/RowActions'
import { enumLabel, ROLE_LABELS } from '../../lib/enumLabels'

type PendingAction =
  | { type: 'role'; user: UserEntity; role: UserRole }
  | { type: 'toggle'; user: UserEntity }

/** Inline, immediately-persisted email editor for one row — not destructive, so no confirm dialog. */
function EmailCell({ user, onSaved }: { user: UserEntity; onSaved: () => void }) {
  const { t } = useTranslation()
  const [value, setValue] = useState(user.email ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setValue(user.email ?? '')
    setError(null)
  }, [user.email])

  const dirty = value !== (user.email ?? '')

  async function handleSave() {
    setSaving(true)
    setError(null)
    try {
      await api.patch(`/users/${user.id}/email`, { email: value })
      onSaved()
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex items-center gap-2">
      <input
        type="email"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={t('users.form.emailOptional')}
        aria-label={t('users.emailAriaLabel', { username: user.username })}
        className="w-48 rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
      />
      {dirty && (
        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className="text-army-700 underline disabled:opacity-50 dark:text-army-300"
        >
          {saving ? t('common.saving') : t('common.save')}
        </button>
      )}
      {error && (
        <span role="alert" className="text-xs text-status-critical">
          {error}
        </span>
      )}
    </div>
  )
}

export default function UsersPage() {
  const { t } = useTranslation()
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

  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null)

  function handleRoleChange(user: UserEntity, role: UserRole) {
    if (role === user.role) return
    setPendingAction({ type: 'role', user, role })
  }

  function handleToggleEnabled(user: UserEntity) {
    setPendingAction({ type: 'toggle', user })
  }

  async function confirmPendingAction() {
    if (!pendingAction) return
    const action = pendingAction
    setPendingAction(null)
    setBanner(null)
    try {
      if (action.type === 'role') {
        await api.patch(`/users/${action.user.id}/role`, { role: action.role })
      } else {
        await api.patch(`/users/${action.user.id}/enabled`, { enabled: !action.user.enabled })
      }
      await loadUsers()
    } catch (err) {
      setBanner(extractApiError(err).message)
    }
  }

  const columns: ColumnDef<UserEntity, unknown>[] = [
    { id: 'id', header: t('common.id'), accessorKey: 'id' },
    { id: 'username', header: t('users.username'), accessorKey: 'username' },
    {
      id: 'email',
      header: t('common.email'),
      cell: ({ row }) => <EmailCell user={row.original} onSaved={loadUsers} />,
    },
    {
      id: 'role',
      header: t('users.role'),
      cell: ({ row }) => (
        <select
          value={
            pendingAction?.type === 'role' && pendingAction.user.id === row.original.id
              ? pendingAction.role
              : row.original.role
          }
          onChange={(e) => handleRoleChange(row.original, e.target.value as UserRole)}
          className="rounded border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
        >
          {Object.entries(ROLE_LABELS).map(([value]) => (
            <option key={value} value={value}>
              {enumLabel(ROLE_LABELS, value)}
            </option>
          ))}
        </select>
      ),
    },
    {
      id: 'enabled',
      header: t('common.status'),
      cell: ({ row }) => (
        <Badge tone={row.original.enabled ? 'ok' : 'neutral'}>
          {row.original.enabled ? t('users.enabled') : t('users.disabled')}
        </Badge>
      ),
    },
    {
      id: 'actions',
      header: t('common.actions'),
      cell: ({ row }) => (
        <div className="flex gap-3">
          <RouteActionLink to={`/users/${row.original.id}/reset-password`}>
            {t('users.resetPassword')}
          </RouteActionLink>
          <button
            type="button"
            onClick={() => handleToggleEnabled(row.original)}
            className={row.original.enabled ? 'text-status-critical underline' : 'text-status-ok underline'}
          >
            {row.original.enabled ? t('users.disable') : t('users.enable')}
          </button>
        </div>
      ),
    },
  ]

  const toggleAction =
    pendingAction?.type === 'toggle' ? (pendingAction.user.enabled ? t('users.disable') : t('users.enable')) : ''

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold">{t('users.title')}</h1>
        <Link
          to="/users/new"
          className="rounded bg-army-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-army-800"
        >
          {t('users.newUser')}
        </Link>
      </div>

      <FormBanner message={banner} />

      <DataTable
        columns={columns}
        data={users}
        loading={loading}
        error={error}
        emptyMessage={t('users.emptyMessage')}
        page={0}
        size={100}
        totalPages={1}
        totalElements={users.length}
        onPageChange={() => {}}
        onSizeChange={() => {}}
      />

      <ConfirmDialog
        open={pendingAction !== null}
        title={
          pendingAction?.type === 'role'
            ? t('users.changeRoleTitle', { username: pendingAction.user.username })
            : t('users.toggleTitle', { action: toggleAction })
        }
        message={
          pendingAction?.type === 'role'
            ? t('users.changeRoleMessage', {
                username: pendingAction.user.username,
                role: enumLabel(ROLE_LABELS, pendingAction.role),
              })
            : t('users.toggleMessage', { action: toggleAction, username: pendingAction?.user.username })
        }
        confirmLabel={pendingAction?.type === 'role' ? t('users.changeRole') : toggleAction}
        onConfirm={confirmPendingAction}
        onCancel={() => setPendingAction(null)}
      />
    </div>
  )
}
