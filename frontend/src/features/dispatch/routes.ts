// Feature: dispatch (Member 6, Dispatch & Service Areas).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  admin: [
    { path: PATHS.adminOverview, page: () => import('./pages/OperationsOverviewPage') },
    { path: PATHS.adminUnassigned, page: () => import('./pages/UnassignedParcelsPage') },
    { path: PATHS.adminFailedDeliveries, page: () => import('./pages/FailedDeliveriesPage') },
  ],
};

export default routes;
