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

/** Up to two initials for an avatar: "Ayesha Khan" -> "AK", "BrightMart" -> "B". */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  const letters = words.length === 1 ? words[0][0] : words[0][0] + words[words.length - 1][0];
  return letters.toUpperCase();
}
