// Feature: notifications (Member 10, Notifications, Events & Deployment).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  customer: [
    { path: PATHS.customerNotifications, page: () => import('./pages/NotificationsPage') },
  ],
};

export default routes;
