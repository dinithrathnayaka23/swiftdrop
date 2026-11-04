// Feature: admin-pricing (Member 3, Delivery Booking & Pricing).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  admin: [{ path: PATHS.adminPricing, page: () => import('./pages/PricingRulesPage') }],
};

export default routes;
