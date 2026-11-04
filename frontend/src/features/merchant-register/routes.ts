// Feature: merchant-register (Member 2, Admin Management, Merchant Approval & Reports).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  guest: [{ path: PATHS.merchantSignup, page: () => import('./pages/MerchantSignupPage') }],
  authPages: [
    { path: PATHS.merchantSubmitted, page: () => import('./pages/MerchantSubmittedPage') },
  ],
};

export default routes;
