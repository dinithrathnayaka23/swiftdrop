/** Must match com.swiftdrop.common.security.Role on the backend. */
export const ROLES = ['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER_ADMIN'] as const;

export type Role = (typeof ROLES)[number];

export interface AuthUser {
  id: string;
  displayName: string;
  role: Role;
}

export interface AuthSession {
  token: string;
  user: AuthUser;
}
