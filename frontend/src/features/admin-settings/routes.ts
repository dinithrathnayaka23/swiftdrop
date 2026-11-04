// Feature: admin-settings (Member 1, Identity, Access & App Shell).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  admin: [{ path: PATHS.adminSettings, page: () => import('./pages/SystemSettingsPage') }],
};

export default routes;
