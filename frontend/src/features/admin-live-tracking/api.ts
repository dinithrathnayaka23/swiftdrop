import { apiClient } from '../../api/client';
import { ApiRequestError, request } from '../../api/errors';
import type { Paged } from '../../types/parcel';
import type {
  LiveDeliveryDetail,
  LiveDeliveryFilters,
  LiveDeliveryRow,
  LiveStatusCounts,
} from './types';

/** Path of the admin live stream, for useLiveUpdates (sends the admin's token). */
export const ADMIN_LIVE_STREAM_PATH = '/admin/live/stream';

/** Drops empty filters so they aren't sent as `?search=`. */
function params(filters: LiveDeliveryFilters): Record<string, string | number> {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== ''),
  ) as Record<string, string | number>;
}

/** Admin Deliveries Tracking (DISPATCHER_ADMIN only). Always the real backend: no mock data. */
export const adminLiveApi = {
  listDeliveries: (filters: LiveDeliveryFilters) =>
    request(() =>
      apiClient.get<Paged<LiveDeliveryRow>>('/admin/live/deliveries', { params: params(filters) }),
    ),

  getCounts: () => request(() => apiClient.get<LiveStatusCounts>('/admin/live/counts')),

  /** Resolves to null when no parcel has this tracking number. */
  async getDelivery(trackingNumber: string): Promise<LiveDeliveryDetail | null> {
    try {
      return await request(() =>
        apiClient.get<LiveDeliveryDetail>(
          `/admin/live/deliveries/${encodeURIComponent(trackingNumber)}`,
        ),
      );
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 404) return null;
      throw err;
    }
  },
};
