// Feature: courier-app (Member 7, Courier App & Proof of Delivery).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  courier: [
    { path: PATHS.courierHome, page: () => import('./pages/TodaysDeliveriesPage') },
    { path: PATHS.courierDeliveryDetail, page: () => import('./pages/DeliveryDetailsPage') },
    { path: PATHS.courierUpdateStatus, page: () => import('./pages/UpdateStatusPage') },
    { path: PATHS.courierProof, page: () => import('./pages/ProofOfDeliveryPage') },
    { path: PATHS.courierFailed, page: () => import('./pages/FailedDeliveryPage') },
    { path: PATHS.courierScan, page: () => import('./pages/ParcelScannerPage') },
    { path: PATHS.courierCompleted, page: () => import('./pages/CompletedDeliveriesPage') },
  ],
};

export default routes;
