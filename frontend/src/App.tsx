import { Navigate, Route, Routes } from 'react-router-dom';
import { AdminLayout } from './components/AdminLayout';
import { PickerLayout } from './components/PickerLayout';
import { RequireAuth } from './components/RequireAuth';
import { RequireRole } from './components/RequireRole';
import { RoleLanding } from './components/RoleLanding';
import { ClaimOrderPage } from './pages/ClaimOrderPage';
import { CompletedOrdersPage } from './pages/CompletedOrdersPage';
import { DashboardPage } from './pages/DashboardPage';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { LoginPage } from './pages/LoginPage';
import { PickerDashboardPage } from './pages/PickerDashboardPage';
import { PickingPage } from './pages/PickingPage';
import { ProductsPage } from './pages/ProductsPage';
import { ReportsPage } from './pages/ReportsPage';
import { SelectWarehousePage } from './pages/SelectWarehousePage';
import { ShelfMappingPage } from './pages/ShelfMappingPage';
import { UploadOrdersPage } from './pages/UploadOrdersPage';
import { UsersPage } from './pages/UsersPage';
import { WarehousesPage } from './pages/WarehousesPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<RequireAuth />}>
        {/* Send each role to its own area. */}
        <Route path="/" element={<RoleLanding />} />
        <Route path="/403" element={<ForbiddenPage />} />

        {/* Admin console — CENTRAL_ADMIN only. */}
        <Route element={<RequireRole allow="CENTRAL_ADMIN" />}>
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="warehouses" element={<WarehousesPage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="shelf-mapping" element={<ShelfMappingPage />} />
            <Route path="upload-orders" element={<UploadOrdersPage />} />
            <Route path="reports" element={<ReportsPage />} />
          </Route>
        </Route>

        {/* Picker app — HUB_PICKER only. */}
        <Route element={<RequireRole allow="HUB_PICKER" />}>
          <Route path="/picker" element={<PickerLayout />}>
            <Route index element={<PickerDashboardPage />} />
            <Route path="select-warehouse" element={<SelectWarehousePage />} />
            <Route path="orders/:id/claim" element={<ClaimOrderPage />} />
            <Route path="orders/:id/pick" element={<PickingPage />} />
            <Route path="completed" element={<CompletedOrdersPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
