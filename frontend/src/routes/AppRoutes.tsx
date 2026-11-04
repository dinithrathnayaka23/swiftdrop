import { Suspense } from 'react';
import { Route, Routes } from 'react-router';
import LoadingState from '../components/feedback/LoadingState';
import NotFoundPage from '../shared/errors/NotFoundPage';
import GuestRoute from '../shared/guards/GuestRoute';
import ProtectedRoute from '../shared/guards/ProtectedRoute';
import AuthLayout from '../shared/layout/AuthLayout';
import CourierLayout from '../shared/layout/CourierLayout';
import DashboardLayout from '../shared/layout/DashboardLayout';
import PublicLayout from '../shared/layout/PublicLayout';
import { pageComponent, routesFor } from './features';
import { PATHS } from './paths';
import type { AppRoute } from './types';

function render(routes: AppRoute[]) {
  return routes.map((route) => {
    const Page = pageComponent(route);
    return <Route key={route.path} path={route.path} element={<Page />} />;
  });
}

/**
 * The router. Screens come from each feature's routes.tsx (see routes/features.ts); this file only
 * decides which layout and sign-in check each area gets. Unknown URLs inside a signed-in area show
 * the 404 page inside that area's layout.
 */
export default function AppRoutes() {
  return (
    <Suspense fallback={<LoadingState className="flex min-h-screen items-center justify-center" />}>
      <Routes>
        {/* Public website with the site header and footer */}
        <Route element={<PublicLayout />}>
          {render(routesFor('public'))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        {/* Sign-in and registration (signed-in users are redirected to their dashboard) */}
        <Route element={<AuthLayout />}>
          <Route element={<GuestRoute />}>{render(routesFor('guest'))}</Route>
          {render(routesFor('authPages'))}
        </Route>
        <Route element={<GuestRoute />}>{render(routesFor('guestFullPage'))}</Route>

        {/* Signed-in areas, one per role */}
        <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
          <Route element={<DashboardLayout />}>
            {render(routesFor('customer'))}
            <Route path={`${PATHS.customerDashboard}/*`} element={<NotFoundPage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['MERCHANT']} />}>
          <Route element={<DashboardLayout />}>
            {render(routesFor('merchant'))}
            <Route path={`${PATHS.merchantDashboard}/*`} element={<NotFoundPage />} />
          </Route>
        </Route>

        <Route
          element={<ProtectedRoute allowedRoles={['COURIER']} loginPath={PATHS.courierLogin} />}
        >
          <Route element={<CourierLayout />}>
            {render(routesFor('courier'))}
            <Route path={`${PATHS.courierHome}/*`} element={<NotFoundPage />} />
          </Route>
        </Route>

        <Route
          element={
            <ProtectedRoute allowedRoles={['DISPATCHER_ADMIN']} loginPath={PATHS.adminLogin} />
          }
        >
          <Route element={<DashboardLayout />}>
            {render(routesFor('admin'))}
            <Route path={`${PATHS.adminOverview}/*`} element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
