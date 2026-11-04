// Shared pages (Member 1): error pages and the development UI kit.
import { PATHS } from '../routes/paths';
import type { FeatureRoutes } from '../routes/types';
import { IS_DEV } from '../utils/env';

const routes: FeatureRoutes = {
  public: [
    { path: PATHS.unauthorized, page: () => import('./errors/UnauthorizedPage') },
    { path: PATHS.sessionExpired, page: () => import('./errors/SessionExpiredPage') },
    ...(IS_DEV ? [{ path: PATHS.uiKit, page: () => import('./dev/UiStatesPage') }] : []),
  ],
};

export default routes;
