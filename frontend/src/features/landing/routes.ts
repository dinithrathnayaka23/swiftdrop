// Feature: landing (Member 5, Public Tracking & Real-time Updates).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  public: [{ path: PATHS.home, page: () => import('./pages/HomePage') }],
};

export default routes;
