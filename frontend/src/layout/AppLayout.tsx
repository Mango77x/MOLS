import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuthStore } from '../auth/store'
import { LOCALE_LABELS, SUPPORTED_LOCALES, type Locale } from '../i18n'
import { enumLabel, ROLE_LABELS } from '../lib/enumLabels'
import { visibleNavItems } from './nav'
import { useLocale } from './useLocale'
import { useTheme } from './useTheme'

/**
 * Authenticated application shell: brand sidebar with role-aware navigation,
 * topbar with theme toggle and session controls, and the routed content.
 */
export default function AppLayout() {
  const { t } = useTranslation()
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const { theme, toggleTheme } = useTheme()
  const { locale, setLocale } = useLocale()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)

  const items = user ? visibleNavItems(user.role) : []

  // A native <dialog> shown via showModal() traps focus and closes on
  // Escape for free — no hand-rolled focus trap needed. We just keep the
  // dialog's open/closed state in sync with `sidebarOpen`.
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (sidebarOpen && !dialog.open) {
      dialog.showModal()
    } else if (!sidebarOpen && dialog.open) {
      dialog.close()
    }
  }, [sidebarOpen])

  function handleDialogClose() {
    setSidebarOpen(false)
    menuButtonRef.current?.focus()
  }

  // The dialog element itself renders the ::backdrop; a click that lands
  // directly on the dialog (not one of its children) is a backdrop click.
  function handleBackdropClick(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === dialogRef.current) {
      setSidebarOpen(false)
    }
  }

  const navigation = (
    <nav className="space-y-1 p-3">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          onClick={() => setSidebarOpen(false)}
          className={({ isActive }) =>
            `block rounded-lg px-3 py-2 text-sm font-medium transition ${
              isActive
                ? 'bg-army-700 text-white'
                : 'text-army-100 hover:bg-army-800 hover:text-white'
            }`
          }
        >
          {t(item.labelKey)}
        </NavLink>
      ))}
    </nav>
  )

  return (
    <div className="flex min-h-full">
      {/* Sidebar (desktop) */}
      <aside className="hidden w-56 shrink-0 bg-army-900 lg:block dark:bg-army-950">
        <div className="flex h-14 items-center px-4 text-lg font-bold tracking-wide text-white">
          MOLS
        </div>
        {navigation}
      </aside>

      {/* Sidebar (mobile, modal dialog) */}
      {/* onClick only closes on a backdrop click (a mouse-only convenience);
          keyboard users already have an equivalent via Escape, which the
          native <dialog> handles for free. */}
      {/* oxlint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions */}
      <dialog
        ref={dialogRef}
        onClose={handleDialogClose}
        onClick={handleBackdropClick}
        aria-label={t('layout.navigationMenu')}
        className="fixed inset-y-0 left-0 m-0 h-full max-h-full w-64 max-w-none border-none bg-army-900 p-0 backdrop:bg-black/50 lg:hidden dark:bg-army-950"
      >
        <div className="flex h-14 items-center justify-between px-4 text-lg font-bold tracking-wide text-white">
          MOLS
          <button
            type="button"
            aria-label={t('layout.closeMenu')}
            onClick={() => setSidebarOpen(false)}
            className="rounded-lg p-1 text-army-100 hover:bg-army-800 hover:text-white"
          >
            ✕
          </button>
        </div>
        {navigation}
      </dialog>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Topbar */}
        <header className="flex h-14 items-center gap-3 border-b border-gray-200 bg-white px-4 dark:border-gray-800 dark:bg-gray-900">
          <button
            ref={menuButtonRef}
            type="button"
            aria-label={t('layout.openMenu')}
            className="rounded-lg p-2 text-gray-600 hover:bg-gray-100 lg:hidden dark:text-gray-300 dark:hover:bg-gray-800"
            onClick={() => setSidebarOpen(true)}
          >
            ☰
          </button>

          <div className="ml-auto flex items-center gap-3">
            <span id="locale-select-label" className="sr-only">
              {t('layout.switchLanguage')}
            </span>
            <select
              value={locale}
              onChange={(e) => setLocale(e.target.value as Locale)}
              aria-labelledby="locale-select-label"
              className="rounded-lg border border-gray-300 bg-white px-2 py-1.5 text-sm text-gray-600 hover:bg-gray-100 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
            >
              {SUPPORTED_LOCALES.map((code) => (
                <option key={code} value={code}>
                  {LOCALE_LABELS[code]}
                </option>
              ))}
            </select>

            <button
              type="button"
              onClick={toggleTheme}
              aria-label={t('layout.toggleTheme')}
              className="rounded-lg p-2 text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
            >
              {theme === 'dark' ? '🌙' : '☀️'}
            </button>

            {user && (
              <span className="text-sm text-gray-600 dark:text-gray-300">
                {user.username}
                <span className="ml-2 rounded-full bg-army-100 px-2 py-0.5 text-xs font-semibold text-army-800 dark:bg-army-900 dark:text-army-200">
                  {enumLabel(ROLE_LABELS, user.role)}
                </span>
              </span>
            )}

            <button
              type="button"
              onClick={() => void logout()}
              className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-100 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
            >
              {t('layout.signOut')}
            </button>
          </div>
        </header>

        <main className="flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
