// Feature: service-areas (Member 6, Dispatch & Service Areas).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  admin: [{ path: PATHS.adminServiceAreas, page: () => import('./pages/ServiceAreasPage') }],
};

export default routes;
