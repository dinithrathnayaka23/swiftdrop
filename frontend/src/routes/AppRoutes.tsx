import { Route, Routes } from 'react-router-dom';
import AppLayout from '../layouts/AppLayout';
import PublicLayout from '../layouts/PublicLayout';
import AdminReportsPage from '../pages/admin/AdminReportsPage';
import AdminUsersPage from '../pages/admin/AdminUsersPage';
import LoginPage from '../pages/auth/LoginPage';
import SignupPage from '../pages/auth/SignupPage';
import CourierHomePage from '../pages/courier/CourierHomePage';
import CreateDeliveryPage from '../pages/customer/CreateDeliveryPage';
import CustomerDashboardPage from '../pages/customer/CustomerDashboardPage';
import MyDeliveriesPage from '../pages/customer/MyDeliveriesPage';
import DispatcherAssignmentPage from '../pages/dispatcher/DispatcherAssignmentPage';
import MerchantDashboardPage from '../pages/merchant/MerchantDashboardPage';
import HomePage from '../pages/public/HomePage';
import NotFoundPage from '../pages/public/NotFoundPage';
import TrackingPage from '../pages/public/TrackingPage';
import UnauthorizedPage from '../pages/public/UnauthorizedPage';
import { PATHS } from './paths';
import ProtectedRoute from './ProtectedRoute';

export default function AppRoutes() {
  return (
    <Routes>
      {/* Public */}
      <Route element={<PublicLayout />}>
        <Route path={PATHS.home} element={<HomePage />} />
        <Route path={PATHS.track} element={<TrackingPage />} />
        <Route path={`${PATHS.track}/:trackingNumber`} element={<TrackingPage />} />
        <Route path={PATHS.login} element={<LoginPage />} />
        <Route path={PATHS.signup} element={<SignupPage />} />
        <Route path={PATHS.unauthorized} element={<UnauthorizedPage />} />
      </Route>

      {/* Signed-in area, guarded per role */}
      <Route element={<AppLayout />}>
        <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
          <Route path={PATHS.customerDashboard} element={<CustomerDashboardPage />} />
          <Route path={PATHS.customerCreateDelivery} element={<CreateDeliveryPage />} />
          <Route path={PATHS.customerDeliveries} element={<MyDeliveriesPage />} />
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['MERCHANT']} />}>
          <Route path={PATHS.merchantDashboard} element={<MerchantDashboardPage />} />
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['COURIER']} />}>
          <Route path={PATHS.courierHome} element={<CourierHomePage />} />
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['DISPATCHER_ADMIN']} />}>
          <Route path={PATHS.dispatcherAssignments} element={<DispatcherAssignmentPage />} />
          <Route path={PATHS.adminReports} element={<AdminReportsPage />} />
          <Route path={PATHS.adminUsers} element={<AdminUsersPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
