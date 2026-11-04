// Feature: reports (Member 2, Admin Management, Merchant Approval & Reports).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  admin: [{ path: PATHS.adminReports, page: () => import('./pages/OperationalReportsPage') }],
};

export default routes;
