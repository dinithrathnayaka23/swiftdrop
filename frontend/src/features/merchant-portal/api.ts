import { apiClient } from '../../api/client';
import { ApiRequestError, request } from '../../api/errors';
import type {
  ApiKey,
  GeneratedApiKey,
  MerchantOrderDetail,
  MerchantOrderSummary,
  MerchantProfile,
  Paged,
  StatusCounts,
  UpdateMerchantProfile,
} from './types';

/** The merchant portal's own endpoints (MERCHANT role, approved merchants only). */
export const merchantPortalApi = {
  /** Number of the merchant's orders per status (every status, 0 when none). */
  getOrderCounts: () => request(() => apiClient.get<StatusCounts>('/merchant/orders/counts')),

  /** The merchant's orders, newest first. */
  listOrders: (page: number, size: number) =>
    request(() =>
      apiClient.get<Paged<MerchantOrderSummary>>('/merchant/orders', { params: { page, size } }),
    ),

  /** One order with its timeline; null when it doesn't exist or isn't this merchant's. */
  async getOrder(trackingNumber: string): Promise<MerchantOrderDetail | null> {
    try {
      return await request(() =>
        apiClient.get<MerchantOrderDetail>(
          `/merchant/orders/${encodeURIComponent(trackingNumber)}`,
        ),
      );
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 404) return null;
      throw err;
    }
  },

  /** All API keys, newest first, including revoked and expired ones. */
  listApiKeys: () => request(() => apiClient.get<ApiKey[]>('/merchant/api-keys')),

  /** 409 when the merchant already has the maximum number of active keys. */
  createApiKey: () => request(() => apiClient.post<GeneratedApiKey>('/merchant/api-keys')),

  revokeApiKey: (apiCredentialId: string) =>
    request(() => apiClient.post<ApiKey>(`/merchant/api-keys/${apiCredentialId}/revoke`)),

  /** Revokes the key and returns its replacement; 409 if the key isn't active. */
  rotateApiKey: (apiCredentialId: string) =>
    request(() => apiClient.post<GeneratedApiKey>(`/merchant/api-keys/${apiCredentialId}/rotate`)),

  getProfile: () => request(() => apiClient.get<MerchantProfile>('/merchant/profile')),

  updateProfile: (profile: UpdateMerchantProfile) =>
    request(() => apiClient.put<MerchantProfile>('/merchant/profile', profile)),
};
