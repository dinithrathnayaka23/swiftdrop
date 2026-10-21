import type { Role } from '../types/auth';

const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Customer',
  MERCHANT: 'Merchant',
  COURIER: 'Courier',
  DISPATCHER_ADMIN: 'Dispatcher / Admin',
};

export function formatRole(role: Role): string {
  return ROLE_LABELS[role];
}
