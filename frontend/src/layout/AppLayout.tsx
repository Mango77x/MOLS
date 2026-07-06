import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuthStore } from '../auth/store'
import { visibleNavItems } from './nav'
import { useTheme } from './useTheme'

/**
 * Authenticated application shell: brand sidebar with role-aware navigation,
 * topbar with theme toggle and session controls, and the routed content.
 */
export default function AppLayout() {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const { theme, toggleTheme } = useTheme()
  const [sidebarOpen, setSidebarOpen] = useState(false)

  const items = user ? visibleNavItems(user.role) : []

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
          {item.label}
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

      {/* Sidebar (mobile, overlay) */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label="Close menu"
            className="absolute inset-0 bg-black/50"
            onClick={() => setSidebarOpen(false)}
          />
          <aside className="absolute inset-y-0 left-0 w-64 bg-army-900 dark:bg-army-950">
            <div className="flex h-14 items-center px-4 text-lg font-bold tracking-wide text-white">
              MOLS
            </div>
            {navigation}
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Topbar */}
        <header className="flex h-14 items-center gap-3 border-b border-gray-200 bg-white px-4 dark:border-gray-800 dark:bg-gray-900">
          <button
            type="button"
            aria-label="Open menu"
            className="rounded-lg p-2 text-gray-600 hover:bg-gray-100 lg:hidden dark:text-gray-300 dark:hover:bg-gray-800"
            onClick={() => setSidebarOpen(true)}
          >
            ☰
          </button>

          <div className="ml-auto flex items-center gap-3">
            <button
              type="button"
              onClick={toggleTheme}
              aria-label="Toggle theme"
              className="rounded-lg p-2 text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
            >
              {theme === 'dark' ? '🌙' : '☀️'}
            </button>

            {user && (
              <span className="text-sm text-gray-600 dark:text-gray-300">
                {user.username}
                <span className="ml-2 rounded-full bg-army-100 px-2 py-0.5 text-xs font-semibold text-army-800 dark:bg-army-900 dark:text-army-200">
                  {user.role}
                </span>
              </span>
            )}

            <button
              type="button"
              onClick={() => void logout()}
              className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-100 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
            >
              Sign out
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
