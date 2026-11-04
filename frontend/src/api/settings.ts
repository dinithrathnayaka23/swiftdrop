import type {
  PublicSettings,
  SystemSettings,
  UpdateSystemSettingsRequest,
} from '../types/settings';
import { apiClient } from './client';
import { request } from './errors';

/** System settings endpoints. */
export const settingsApi = {
  /** Contact details, display formats, parcel limits and maintenance mode. No sign-in needed. */
  getPublic: () => request(() => apiClient.get<PublicSettings>('/public/settings')),
  /** Admin only. */
  get: () => request(() => apiClient.get<SystemSettings>('/admin/settings')),
  /** Admin only. 409 if the settings changed since they were loaded. */
  update: (body: UpdateSystemSettingsRequest) =>
    request(() => apiClient.put<SystemSettings>('/admin/settings', body)),
};
