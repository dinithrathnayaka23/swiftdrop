import { apiClient } from '../../api/client';
import { ApiRequestError, request } from '../../api/errors';

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
};
