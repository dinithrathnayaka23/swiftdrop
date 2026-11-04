import type { Role } from '../types/auth';

/**
 * Every screen's URL, pre-created for the whole team (see src/features/README.md). Paths with
 * ":param" are patterns: build a link with generatePath(PATHS.adminParcelDetail, { parcelId }).
 * Add a path here only through Member 1, so this file never conflicts.
 */
export const PATHS = {
  // ---- Public (landing, public-tracking) ----
  home: '/',
  track: '/track',
  /** Public tracking result: /track/SD123456789 */
  trackResult: '/track/:trackingNumber',

  // ---- Sign-in and registration (auth, merchant-register) ----
  login: '/login',
  /** Courier app sign-in (phones). Couriers are added by an admin, so there is no sign-up. */
  courierLogin: '/courier/login',
  /** Admin portal sign-in. */
  adminLogin: '/admin/login',
  forgotPassword: '/forgot-password',
  /** Opened from the password reset email: /reset-password?token=... */
  resetPassword: '/reset-password',
  signup: '/signup',
  merchantSignup: '/signup/merchant',
  merchantSubmitted: '/signup/merchant/submitted',
  /** After customer sign-up: "confirm your email". */
  checkEmail: '/signup/check-email',
  /** Opened from the confirmation email: /verify-email?token=... */
  verifyEmail: '/verify-email',

  // ---- Error pages (shared) ----
  unauthorized: '/unauthorized',
  sessionExpired: '/session-expired',
  /** Development only: every shared UI state on one page. */
  uiKit: '/dev/ui',

  // ---- Customer ----
  customerDashboard: '/customer',
  customerBook: '/customer/book',
  customerDeliveries: '/customer/deliveries',
  /** Logged-in tracking of one of the customer's own parcels, with the receipt download. */
  customerDeliveryDetail: '/customer/deliveries/:trackingNumber',
  customerNotifications: '/customer/notifications',
  customerProfile: '/customer/profile',

  // ---- Merchant ----
  merchantDashboard: '/merchant',
  merchantOrders: '/merchant/orders',
  merchantOrderDetail: '/merchant/orders/:parcelId',
  merchantIntegration: '/merchant/integration',
  merchantDeliveryUpdates: '/merchant/delivery-updates',
  merchantProfile: '/merchant/profile',

  // ---- Courier app ----
  courierHome: '/courier',
  courierDeliveryDetail: '/courier/deliveries/:parcelId',
  courierUpdateStatus: '/courier/deliveries/:parcelId/status',
  courierProof: '/courier/deliveries/:parcelId/proof',
  courierFailed: '/courier/deliveries/:parcelId/failed',
  courierScan: '/courier/scan',
  courierCompleted: '/courier/completed',
  courierAccount: '/courier/account',

  // ---- Admin / dispatcher ----
  adminOverview: '/admin',
  adminUnassigned: '/admin/dispatch/unassigned',
  adminFailedDeliveries: '/admin/dispatch/failed',
  adminParcels: '/admin/parcels',
  adminParcelDetail: '/admin/parcels/:parcelId',
  adminDeliveries: '/admin/deliveries',
  adminCouriers: '/admin/couriers',
  adminMerchants: '/admin/merchants',
  adminMerchantDetail: '/admin/merchants/:merchantId',
  adminPricing: '/admin/pricing',
  adminServiceAreas: '/admin/service-areas',
  adminReports: '/admin/reports',
  adminUsers: '/admin/users',
  adminSettings: '/admin/settings',
} as const;

/** Where each role lands after login. */
export const ROLE_HOME: Record<Role, string> = {
  CUSTOMER: PATHS.customerDashboard,
  MERCHANT: PATHS.merchantDashboard,
  COURIER: PATHS.courierHome,
  DISPATCHER_ADMIN: PATHS.adminOverview,
};

/** Each role's sign-in page; the backend refuses a sign-in on another role's page. */
export const ROLE_SIGN_IN: Record<Role, string> = {
  CUSTOMER: PATHS.login,
  MERCHANT: PATHS.login,
  COURIER: PATHS.courierLogin,
  DISPATCHER_ADMIN: PATHS.adminLogin,
};

/** Each role's account/profile page, linked from the user menu. */
export const ROLE_ACCOUNT: Record<Role, string> = {
  CUSTOMER: PATHS.customerProfile,
  MERCHANT: PATHS.merchantProfile,
  COURIER: PATHS.courierAccount,
  DISPATCHER_ADMIN: PATHS.adminSettings,
};
