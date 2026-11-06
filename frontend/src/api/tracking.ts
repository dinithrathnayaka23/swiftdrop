import { isAxiosError } from 'axios';
import type { TrackingApi, TrackingResult } from '../types/tracking';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { ApiRequestError, toApiRequestError } from './errors';
import { mockTrackingApi } from './mock/mockTrackingApi';

/** Used when a 429 has no usable Retry-After header (the backend's window is one minute). */
const DEFAULT_RETRY_AFTER_SECONDS = 60;

/** 429 from the tracking API: too many look-ups from this browser's IP for now. */
export class TrackingRateLimitError extends ApiRequestError {
  /** Whole seconds to wait before searching again (from the Retry-After header). */
  readonly retryAfterSeconds: number;

  constructor(message: string, retryAfterSeconds: number) {
    super(message, { status: 429, code: 'TOO_MANY_REQUESTS' });
    this.name = 'TrackingRateLimitError';
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/** Retry-After is either delay-seconds or an HTTP date. */
export function parseRetryAfter(value: unknown, now = Date.now()): number {
  if (typeof value !== 'string' || value.trim() === '') return DEFAULT_RETRY_AFTER_SECONDS;
  const trimmed = value.trim();
  if (/^\d+$/.test(trimmed)) return Math.max(1, Number(trimmed));
  const date = Date.parse(trimmed);
  if (Number.isNaN(date)) return DEFAULT_RETRY_AFTER_SECONDS;
  return Math.max(1, Math.ceil((date - now) / 1000));
}

const httpTrackingApi: TrackingApi = {
  async getTracking(trackingNumber) {
    try {
      const { data } = await apiClient.get<TrackingResult>(
        `/public/tracking/${encodeURIComponent(trackingNumber)}`,
      );
      return data;
    } catch (err) {
      // Read Retry-After here: the shared error conversion doesn't keep response headers.
      if (isAxiosError(err) && err.response?.status === 429) {
        const error = toApiRequestError(err);
        throw new TrackingRateLimitError(
          error.message,
          parseRetryAfter(err.response.headers['retry-after']),
        );
      }
      const error = toApiRequestError(err);
      if (error.status === 404) return null;
      throw error;
    }
  },
};

export const trackingApi: TrackingApi = isMockApiEnabled() ? mockTrackingApi : httpTrackingApi;
