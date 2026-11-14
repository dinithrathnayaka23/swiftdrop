import { apiClient } from '../../api/client';
import { request } from '../../api/errors';
import type { Merchant, MerchantStatus } from './types';

/** Admin merchant approval (DISPATCHER_ADMIN only). */
export const adminMerchantsApi = {
  /** Merchants with this approval status, oldest first. */
  list: (status: MerchantStatus) =>
    request(() => apiClient.get<Merchant[]>('/admin/merchants', { params: { status } })),

  /** Approves a pending merchant; 409 if it was already reviewed. */
  approve: (merchantId: string) =>
    request(() => apiClient.post<Merchant>(`/admin/merchants/${merchantId}/approve`)),

  /** Rejects a pending merchant with a reason the merchant is emailed; 409 if already reviewed. */
  reject: (merchantId: string, reason: string) =>
    request(() => apiClient.post<Merchant>(`/admin/merchants/${merchantId}/reject`, { reason })),
};
