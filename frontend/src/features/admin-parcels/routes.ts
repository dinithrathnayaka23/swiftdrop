// Feature: admin-parcels (Member 4, Parcel Status, History & Customer Deliveries).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  admin: [
    { path: PATHS.adminParcels, page: () => import('./pages/ParcelListPage') },
    { path: PATHS.adminParcelDetail, page: () => import('./pages/ParcelDetailPage') },
  ],
};

export default routes;
