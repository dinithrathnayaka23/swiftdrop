import { apiClient } from '../../api/client';
import { ApiRequestError, request } from '../../api/errors';
import type { Merchant, MerchantStatus } from './types';

/** Admin merchant approval (DISPATCHER_ADMIN only). */
export const adminMerchantsApi = {
  /** Merchants with this approval status, oldest first. */
  list: (status: MerchantStatus) =>
    request(() => apiClient.get<Merchant[]>('/admin/merchants', { params: { status } })),

  /** One merchant with its review details; null when there is none with this ID. */
  async get(merchantId: string): Promise<Merchant | null> {
    try {
      return await request(() =>
        apiClient.get<Merchant>(`/admin/merchants/${encodeURIComponent(merchantId)}`),
      );
    } catch (err) {
      // 404: no such merchant. 400: the ID in the URL isn't a valid ID at all.
      if (err instanceof ApiRequestError && (err.status === 404 || err.status === 400)) return null;
      throw err;
    }
  },

  /** Approves a pending merchant; 409 if it was already reviewed. */
  approve: (merchantId: string) =>
    request(() => apiClient.post<Merchant>(`/admin/merchants/${merchantId}/approve`)),

  /** Rejects a pending merchant with a reason the merchant is emailed; 409 if already reviewed. */
  reject: (merchantId: string, reason: string) =>
    request(() => apiClient.post<Merchant>(`/admin/merchants/${merchantId}/reject`, { reason })),
};
