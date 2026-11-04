// Feature: customer-dashboard (Member 4, Parcel Status, History & Customer Deliveries).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  customer: [
    { path: PATHS.customerDashboard, page: () => import('./pages/CustomerDashboardPage') },
  ],
};

export default routes;
