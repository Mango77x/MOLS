import { useEffect } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import LoginPage from './auth/LoginPage'
import RequireAuth from './auth/RequireAuth'
import { useAuthStore } from './auth/store'
import AppLayout from './layout/AppLayout'
import ComingSoon from './pages/ComingSoon'
import DashboardPage from './pages/DashboardPage'
import MovementsPage from './pages/movements/MovementsPage'
import OrdersPage from './pages/orders/OrdersPage'
import ResourcesPage from './pages/resources/ResourcesPage'
import ShipmentsPage from './pages/shipments/ShipmentsPage'
import StocksPage from './pages/stocks/StocksPage'
import UnitsPage from './pages/units/UnitsPage'
import VehiclesPage from './pages/vehicles/VehiclesPage'
import WarehousesPage from './pages/warehouses/WarehousesPage'

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
          <Route path="warehouses" element={<WarehousesPage />} />
          <Route path="resources" element={<ResourcesPage />} />
          <Route path="vehicles" element={<VehiclesPage />} />
          <Route path="stocks" element={<StocksPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="shipments" element={<ShipmentsPage />} />
          <Route path="units" element={<UnitsPage />} />
          <Route path="movements" element={<MovementsPage />} />
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
