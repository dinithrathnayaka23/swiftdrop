import { Route, Routes } from 'react-router';
import AppLayout from '../layouts/AppLayout';
import AuthLayout from '../layouts/AuthLayout';
import CourierLayout from '../layouts/CourierLayout';
import PublicLayout from '../layouts/PublicLayout';
import AdminReportsPage from '../pages/admin/AdminReportsPage';
import AdminUsersPage from '../pages/admin/AdminUsersPage';
import AdminLoginPage from '../pages/auth/AdminLoginPage';
import CheckEmailPage from '../pages/auth/CheckEmailPage';
import CustomerSignupPage from '../pages/auth/CustomerSignupPage';
import ForgotPasswordPage from '../pages/auth/ForgotPasswordPage';
import LoginPage from '../pages/auth/LoginPage';
import MerchantSignupPage from '../pages/auth/MerchantSignupPage';
import MerchantSubmittedPage from '../pages/auth/MerchantSubmittedPage';
import ResetPasswordPage from '../pages/auth/ResetPasswordPage';
import VerifyEmailPage from '../pages/auth/VerifyEmailPage';
import CourierHomePage from '../pages/courier/CourierHomePage';
import CourierLoginPage from '../pages/courier/CourierLoginPage';
import CreateDeliveryPage from '../pages/customer/CreateDeliveryPage';
import CustomerDashboardPage from '../pages/customer/CustomerDashboardPage';
import MyDeliveriesPage from '../pages/customer/MyDeliveriesPage';
import UiStatesPage from '../pages/dev/UiStatesPage';
import DispatcherAssignmentPage from '../pages/dispatcher/DispatcherAssignmentPage';
import MerchantDashboardPage from '../pages/merchant/MerchantDashboardPage';
import HomePage from '../pages/public/HomePage';
import NotFoundPage from '../pages/public/NotFoundPage';
import SessionExpiredPage from '../pages/public/SessionExpiredPage';
import TrackingPage from '../pages/public/TrackingPage';
import UnauthorizedPage from '../pages/public/UnauthorizedPage';
import GuestRoute from './GuestRoute';
import { PATHS } from './paths';
import ProtectedRoute from './ProtectedRoute';
import { IS_DEV } from '../utils/env';

export default function AppRoutes() {
  return (
    <Routes>
      {/* Public pages with the site header */}
      <Route element={<PublicLayout />}>
        <Route path={PATHS.home} element={<HomePage />} />
        <Route path={PATHS.track} element={<TrackingPage />} />
        <Route path={`${PATHS.track}/:trackingNumber`} element={<TrackingPage />} />
        <Route path={PATHS.unauthorized} element={<UnauthorizedPage />} />
        <Route path={PATHS.sessionExpired} element={<SessionExpiredPage />} />
        {IS_DEV && <Route path={PATHS.uiKit} element={<UiStatesPage />} />}
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      {/* Sign-in and registration (signed-in users are redirected to their dashboard) */}
      <Route element={<AuthLayout />}>
        <Route element={<GuestRoute />}>
          <Route path={PATHS.login} element={<LoginPage />} />
          <Route path={PATHS.adminLogin} element={<AdminLoginPage />} />
          <Route path={PATHS.forgotPassword} element={<ForgotPasswordPage />} />
          <Route path={PATHS.resetPassword} element={<ResetPasswordPage />} />
          <Route path={PATHS.signup} element={<CustomerSignupPage />} />
          <Route path={PATHS.merchantSignup} element={<MerchantSignupPage />} />
        </Route>
        <Route path={PATHS.merchantSubmitted} element={<MerchantSubmittedPage />} />
        <Route path={PATHS.checkEmail} element={<CheckEmailPage />} />
        <Route path={PATHS.verifyEmail} element={<VerifyEmailPage />} />
      </Route>

      {/* Courier app: its own phone-first sign-in page and shell */}
      <Route element={<GuestRoute />}>
        <Route path={PATHS.courierLogin} element={<CourierLoginPage />} />
      </Route>
      <Route element={<ProtectedRoute allowedRoles={['COURIER']} loginPath={PATHS.courierLogin} />}>
        <Route element={<CourierLayout />}>
          <Route path={PATHS.courierHome} element={<CourierHomePage />} />
        </Route>
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

        <Route
          element={
            <ProtectedRoute allowedRoles={['DISPATCHER_ADMIN']} loginPath={PATHS.adminLogin} />
          }
        >
          <Route path={PATHS.dispatcherAssignments} element={<DispatcherAssignmentPage />} />
          <Route path={PATHS.adminReports} element={<AdminReportsPage />} />
          <Route path={PATHS.adminUsers} element={<AdminUsersPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
