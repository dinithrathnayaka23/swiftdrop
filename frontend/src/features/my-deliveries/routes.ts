// Feature: my-deliveries (Member 4, Parcel Status, History & Customer Deliveries).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  customer: [
    { path: PATHS.customerDeliveries, page: () => import('./pages/MyDeliveriesPage') },
    { path: PATHS.customerDeliveryDetail, page: () => import('./pages/DeliveryTrackingPage') },
  ],
};

export default routes;
