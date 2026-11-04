// Feature: auth (Member 1, Identity, Access & App Shell).
// Owned by that member: add screens here and in ./pages. Paths come from routes/paths.ts.
import { PATHS } from '../../routes/paths';
import type { FeatureRoutes } from '../../routes/types';

const routes: FeatureRoutes = {
  guest: [
    { path: PATHS.login, page: () => import('./pages/LoginPage') },
    { path: PATHS.adminLogin, page: () => import('./pages/AdminLoginPage') },
    { path: PATHS.forgotPassword, page: () => import('./pages/ForgotPasswordPage') },
    { path: PATHS.resetPassword, page: () => import('./pages/ResetPasswordPage') },
    { path: PATHS.signup, page: () => import('./pages/CustomerSignupPage') },
  ],
  guestFullPage: [{ path: PATHS.courierLogin, page: () => import('./pages/CourierLoginPage') }],
  authPages: [
    { path: PATHS.checkEmail, page: () => import('./pages/CheckEmailPage') },
    { path: PATHS.verifyEmail, page: () => import('./pages/VerifyEmailPage') },
  ],
  courier: [{ path: PATHS.courierAccount, page: () => import('./pages/AccountPage') }],
};

export default routes;
