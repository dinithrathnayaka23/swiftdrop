import { apiClient } from '../../api/client';
import { ApiRequestError, request } from '../../api/errors';
import type { Paged } from '../../types/parcel';

export interface WebhookSettings {
  webhookId: string;
  endpointUrl: string;
  eventTypes: string[];
  active: boolean;
  signingSecret: string | null;
  createdAt: string;
}

export interface UpdateWebhookSettings {
  endpointUrl: string;
  eventTypes: string[];
  active: boolean;
}

export type WebhookDeliveryStatus = 'PENDING' | 'DELIVERED' | 'FAILED';

export interface WebhookDelivery {
  deliveryId: string;
  parcelId: string;
  trackingNumber?: string | null;
  eventType: string;
  status: WebhookDeliveryStatus;
  retryCount: number;
  lastAttemptAt: string | null;
  lastHttpStatus: number | null;
  lastResponseExcerpt: string | null;
  lastErrorMessage: string | null;
  nextRetryAt: string | null;
  deliveredAt: string | null;
  createdAt: string;
}

export interface WebhookTestResult {
  delivered: boolean;
  httpStatus: number | null;
  responseExcerpt: string | null;
  errorMessage: string | null;
}

const EMPTY_SETTINGS: WebhookSettings = {
  webhookId: '',
  endpointUrl: '',
  eventTypes: [],
  active: false,
  signingSecret: null,
  createdAt: '',
};

export const merchantWebhooksApi = {
  async get(): Promise<WebhookSettings> {
    try {
      return await request(() => apiClient.get<WebhookSettings>('/merchant/webhook'));
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 404) return EMPTY_SETTINGS;
      throw error;
    }
  },

  update: (settings: UpdateWebhookSettings) =>
    request(() => apiClient.put<WebhookSettings>('/merchant/webhook', settings)),

  rotateSecret: () =>
    request(() => apiClient.post<WebhookSettings>('/merchant/webhook/rotate-secret')),

  listDeliveries: (page: number, status?: WebhookDeliveryStatus) =>
    request(() =>
      apiClient.get<Paged<WebhookDelivery>>('/merchant/webhook/deliveries', {
        params: { page, size: 10, ...(status ? { status } : {}) },
      }),
    ),

  retryDelivery: (deliveryId: string) =>
    request(() =>
      apiClient.post<WebhookDelivery>(`/merchant/webhook/deliveries/${deliveryId}/retry`),
    ),

  sendTest: () => request(() => apiClient.post<WebhookTestResult>('/merchant/webhook/test')),
};
