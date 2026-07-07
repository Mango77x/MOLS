import { useEffect } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import LoginPage from './auth/LoginPage'
import RequireAuth from './auth/RequireAuth'
import SetupPage from './auth/SetupPage'
import { useAuthStore } from './auth/store'
import AppLayout from './layout/AppLayout'
import DashboardPage from './pages/DashboardPage'
import MovementsPage from './pages/movements/MovementsPage'
import OrderDetailPage from './pages/orders/OrderDetailPage'
import OrderEditFormPage from './pages/orders/OrderEditFormPage'
import OrderWizardPage from './pages/orders/OrderWizardPage'
import OrdersPage from './pages/orders/OrdersPage'
import ResourceFormPage from './pages/resources/ResourceFormPage'
import ResourcesPage from './pages/resources/ResourcesPage'
import ShipmentDetailPage from './pages/shipments/ShipmentDetailPage'
import ShipmentFormPage from './pages/shipments/ShipmentFormPage'
import ShipmentsPage from './pages/shipments/ShipmentsPage'
import StockAdjustFormPage from './pages/stocks/StockAdjustFormPage'
import StockCreateFormPage from './pages/stocks/StockCreateFormPage'
import StocksPage from './pages/stocks/StocksPage'
import UnitFormPage from './pages/units/UnitFormPage'
import UnitsPage from './pages/units/UnitsPage'
import UserFormPage from './pages/users/UserFormPage'
import UserResetPasswordPage from './pages/users/UserResetPasswordPage'
import UsersPage from './pages/users/UsersPage'
import VehicleFormPage from './pages/vehicles/VehicleFormPage'
import VehiclesPage from './pages/vehicles/VehiclesPage'
import WarehouseFormPage from './pages/warehouses/WarehouseFormPage'
import WarehousesPage from './pages/warehouses/WarehousesPage'

/**
 * Application routes. The SPA is served under /app (see vite.config.ts);
 * every route except /login requires an authenticated session. Form/detail
 * routes are additionally role-gated to mirror the API's role matrix
 * (ADMIN-only master data + stock; ADMIN+OPERATOR for orders/shipments).
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
        <Route path="/setup" element={<SetupPage />} />

        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />

          <Route path="warehouses" element={<WarehousesPage />} />
          <Route
            path="warehouses/new"
            element={
              <RequireAuth roles={['ADMIN']}>
                <WarehouseFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="warehouses/:id/edit"
            element={
              <RequireAuth roles={['ADMIN']}>
                <WarehouseFormPage />
              </RequireAuth>
            }
          />

          <Route path="resources" element={<ResourcesPage />} />
          <Route
            path="resources/new"
            element={
              <RequireAuth roles={['ADMIN']}>
                <ResourceFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="resources/:id/edit"
            element={
              <RequireAuth roles={['ADMIN']}>
                <ResourceFormPage />
              </RequireAuth>
            }
          />

          <Route path="vehicles" element={<VehiclesPage />} />
          <Route
            path="vehicles/new"
            element={
              <RequireAuth roles={['ADMIN']}>
                <VehicleFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="vehicles/:id/edit"
            element={
              <RequireAuth roles={['ADMIN']}>
                <VehicleFormPage />
              </RequireAuth>
            }
          />

          <Route path="units" element={<UnitsPage />} />
          <Route
            path="units/new"
            element={
              <RequireAuth roles={['ADMIN']}>
                <UnitFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="units/:id/edit"
            element={
              <RequireAuth roles={['ADMIN']}>
                <UnitFormPage />
              </RequireAuth>
            }
          />

          <Route path="stocks" element={<StocksPage />} />
          <Route
            path="stocks/new"
            element={
              <RequireAuth roles={['ADMIN']}>
                <StockCreateFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="stocks/:id/adjust"
            element={
              <RequireAuth roles={['ADMIN']}>
                <StockAdjustFormPage />
              </RequireAuth>
            }
          />

          <Route path="orders" element={<OrdersPage />} />
          <Route
            path="orders/new"
            element={
              <RequireAuth roles={['ADMIN', 'OPERATOR']}>
                <OrderWizardPage />
              </RequireAuth>
            }
          />
          <Route path="orders/:id" element={<OrderDetailPage />} />
          <Route
            path="orders/:id/edit"
            element={
              <RequireAuth roles={['ADMIN', 'OPERATOR']}>
                <OrderEditFormPage />
              </RequireAuth>
            }
          />

          <Route path="shipments" element={<ShipmentsPage />} />
          <Route
            path="shipments/new"
            element={
              <RequireAuth roles={['ADMIN', 'OPERATOR']}>
                <ShipmentFormPage />
              </RequireAuth>
            }
          />
          <Route path="shipments/:id" element={<ShipmentDetailPage />} />
          <Route
            path="shipments/:id/edit"
            element={
              <RequireAuth roles={['ADMIN', 'OPERATOR']}>
                <ShipmentFormPage />
              </RequireAuth>
            }
          />

          <Route path="movements" element={<MovementsPage />} />

          <Route
            path="users"
            element={
              <RequireAuth roles={['ADMIN']}>
                <UsersPage />
              </RequireAuth>
            }
          />
          <Route
            path="users/new"
            element={
              <RequireAuth roles={['ADMIN']}>
                <UserFormPage />
              </RequireAuth>
            }
          />
          <Route
            path="users/:id/reset-password"
            element={
              <RequireAuth roles={['ADMIN']}>
                <UserResetPasswordPage />
              </RequireAuth>
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
