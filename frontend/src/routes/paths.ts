import type { Role } from '../types/auth';

export const PATHS = {
  home: '/',
  login: '/login',
  signup: '/signup',
  track: '/track',
  unauthorized: '/unauthorized',

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
