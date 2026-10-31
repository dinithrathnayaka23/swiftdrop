import type { Role } from '../types/auth';

export const PATHS = {
  home: '/',
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
  track: '/track',
  unauthorized: '/unauthorized',
  sessionExpired: '/session-expired',
  uiKit: '/dev/ui',

  customerDashboard: '/customer',
  customerCreateDelivery: '/customer/deliveries/new',
  customerDeliveries: '/customer/deliveries',

  merchantDashboard: '/merchant',

  courierHome: '/courier',

  dispatcherAssignments: '/dispatcher/assignments',

  adminReports: '/admin/reports',
  adminUsers: '/admin/users',
} as const;

/** Where each role lands after login. */
export const ROLE_HOME: Record<Role, string> = {
  CUSTOMER: PATHS.customerDashboard,
  MERCHANT: PATHS.merchantDashboard,
  COURIER: PATHS.courierHome,
  DISPATCHER_ADMIN: PATHS.dispatcherAssignments,
};

/** Each role's sign-in page; the backend refuses a sign-in on another role's page. */
export const ROLE_SIGN_IN: Record<Role, string> = {
  CUSTOMER: PATHS.login,
  MERCHANT: PATHS.login,
  COURIER: PATHS.courierLogin,
  DISPATCHER_ADMIN: PATHS.adminLogin,
};

export interface NavItem {
  label: string;
  to: string;
}

/** Sidebar entries per role. Add items here as screens are built. */
export const NAV_ITEMS: Record<Role, NavItem[]> = {
  CUSTOMER: [
    { label: 'Dashboard', to: PATHS.customerDashboard },
    { label: 'Create delivery', to: PATHS.customerCreateDelivery },
    { label: 'My deliveries', to: PATHS.customerDeliveries },
  ],
  MERCHANT: [{ label: 'Dashboard', to: PATHS.merchantDashboard }],
  COURIER: [{ label: 'My jobs', to: PATHS.courierHome }],
  DISPATCHER_ADMIN: [
    { label: 'Assignments', to: PATHS.dispatcherAssignments },
    { label: 'Reports', to: PATHS.adminReports },
    { label: 'Users', to: PATHS.adminUsers },
  ],
};
