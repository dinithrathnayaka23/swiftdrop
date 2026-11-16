import { apiClient } from '../../api/client';
import { request } from '../../api/errors';
import type { Role } from '../../types/auth';
import type { Paged } from '../../types/parcel';
import type { AccountStatus, UserFilters, UserStats, UserSummary } from './types';

/** Drops empty filters so they aren't sent as `?search=`. */
function params(filters: UserFilters): Record<string, string | number> {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== ''),
  ) as Record<string, string | number>;
}

/** Admin User Management (DISPATCHER_ADMIN only). */
export const adminUsersApi = {
  /** Accounts, newest first, filtered by name/email/phone text, role and status. */
  list: (filters: UserFilters) =>
    request(() => apiClient.get<Paged<UserSummary>>('/admin/users', { params: params(filters) })),

  /** Account counts in total, per role and per status. */
  stats: () => request(() => apiClient.get<UserStats>('/admin/users/stats')),

  /** Customer ↔ courier only; 409 for the admin and merchant roles. */
  changeRole: (userId: string, role: Role) =>
    request(() => apiClient.patch<UserSummary>(`/admin/users/${userId}/role`, { role })),

  /** ACTIVE, SUSPENDED or DEACTIVATED; 409 for the admin account and pending merchants. */
  changeStatus: (userId: string, status: AccountStatus) =>
    request(() => apiClient.patch<UserSummary>(`/admin/users/${userId}/status`, { status })),
};
