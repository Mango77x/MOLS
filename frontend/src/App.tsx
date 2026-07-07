import { useEffect } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import LoginPage from './auth/LoginPage'
import RequireAuth from './auth/RequireAuth'
import { useAuthStore } from './auth/store'
import AppLayout from './layout/AppLayout'
import ComingSoon from './pages/ComingSoon'
import DashboardPage from './pages/DashboardPage'

/**
 * Application routes. The SPA is served under /app (see vite.config.ts);
 * every route except /login requires an authenticated session.
 */
export default function App() {
  const restore = useAuthStore((state) => state.restore)

  useEffect(() => {
    void restore()
  }, [restore])

  return (
    <BrowserRouter basename="/app">
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route
            path="warehouses"
            element={<ComingSoon title="Warehouses" uiPath="/ui/warehouses" />}
          />
          <Route
            path="resources"
            element={<ComingSoon title="Resources" uiPath="/ui/resources" />}
          />
          <Route
            path="vehicles"
            element={<ComingSoon title="Vehicles" uiPath="/ui/vehicles" />}
          />
          <Route path="stocks" element={<ComingSoon title="Stock" uiPath="/ui/stocks" />} />
          <Route path="orders" element={<ComingSoon title="Orders" uiPath="/ui/orders" />} />
          <Route
            path="shipments"
            element={<ComingSoon title="Shipments" uiPath="/ui/shipments" />}
          />
          <Route path="units" element={<ComingSoon title="Units" uiPath="/ui/units" />} />
          <Route
            path="movements"
            element={<ComingSoon title="Audit log" uiPath="/ui/movements" />}
          />
          <Route
            path="users"
            element={
              <RequireAuth adminOnly>
                <ComingSoon title="Users" uiPath="/ui/users" />
              </RequireAuth>
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
