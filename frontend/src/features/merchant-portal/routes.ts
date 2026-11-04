// Feature: merchant-portal (Member 8, Merchant Portal & API Keys).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  merchant: [
    { path: PATHS.merchantDashboard, page: () => import('./pages/MerchantDashboardPage') },
    { path: PATHS.merchantOrders, page: () => import('./pages/OrdersPage') },
    { path: PATHS.merchantOrderDetail, page: () => import('./pages/OrderDetailsPage') },
    { path: PATHS.merchantIntegration, page: () => import('./pages/ApiIntegrationPage') },
    { path: PATHS.merchantProfile, page: () => import('./pages/BusinessProfilePage') },
  ],
};

export default routes;
