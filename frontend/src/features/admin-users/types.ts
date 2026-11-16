import type { Role } from '../../types/auth';

/** Must match com.swiftdrop.user.model.AccountStatus on the backend. */
export const ACCOUNT_STATUSES = ['ACTIVE', 'PENDING_APPROVAL', 'SUSPENDED', 'DEACTIVATED'] as const;
export type AccountStatus = (typeof ACCOUNT_STATUSES)[number];

/** One account in the admin user list (com.swiftdrop.user.api.UserSummary). */
export interface UserSummary {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  role: Role;
  accountStatus: AccountStatus;
  emailVerified: boolean;
  createdAt: string;
}

/** Filters for the user list; empty values are not sent. */
export interface UserFilters {
  search?: string;
  role?: Role | '';
  status?: AccountStatus | '';
  page?: number;
  size?: number;
}

/** Dashboard card numbers (GET /admin/users/stats). */
export interface UserStats {
  total: number;
  byRole: Record<Role, number>;
  byStatus: Record<AccountStatus, number>;
}
