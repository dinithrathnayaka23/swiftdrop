// Feature: admin-live-tracking (Member 5, Public Tracking & Real-time Updates).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  admin: [{ path: PATHS.adminDeliveries, page: () => import('./pages/LiveTrackingPage') }],
};

export default routes;
