import type { TrackingApi, TrackingResult } from '../types/tracking';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { ApiRequestError, request } from './errors';
import { mockTrackingApi } from './mock/mockTrackingApi';

const httpTrackingApi: TrackingApi = {
  async getTracking(trackingNumber) {
    try {
      return await request(() =>
        apiClient.get<TrackingResult>(`/public/tracking/${encodeURIComponent(trackingNumber)}`),
      );
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 404) return null;
      throw err;
    }
  },
};

export const trackingApi: TrackingApi = isMockApiEnabled() ? mockTrackingApi : httpTrackingApi;
