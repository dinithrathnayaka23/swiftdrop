// Feature: admin-live-tracking (Member 5, Public Tracking & Real-time Updates).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';
import { DELIVERY_DETAIL_PATH } from './paths';

const routes: FeatureRoutes = {
  admin: [
    { path: PATHS.adminDeliveries, page: () => import('./pages/LiveTrackingPage') },
    { path: DELIVERY_DETAIL_PATH, page: () => import('./pages/DeliveryDetailPage') },
  ],
};

export default routes;
