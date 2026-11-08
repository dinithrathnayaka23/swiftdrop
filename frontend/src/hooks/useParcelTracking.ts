import { useEffect, useState } from 'react';
import { getErrorMessage } from '../api/errors';
import { trackingApi, TrackingRateLimitError } from '../api/tracking';
import type { TrackingResult } from '../types/tracking';

export type TrackingState =
  | { status: 'loading' }
  | { status: 'found'; data: TrackingResult }
  | { status: 'not-found' }
  /** Too many look-ups (429): wait this long before searching again. */
  | { status: 'rate-limited'; retryAfterSeconds: number }
  | { status: 'error'; message: string };

/**
 * Loads public tracking info; `retry()` loads it again (after an error or a rate limit).
 *
 * A 503 for maintenance needs nothing here: the API client switches the app to the maintenance
 * page for every non-admin screen.
 */
export function useParcelTracking(trackingNumber: string): TrackingState & { retry: () => void } {
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<{ key: string; state: TrackingState } | null>(null);
  const key = `${trackingNumber}#${attempt}`;

  useEffect(() => {
    let active = true;
    trackingApi.getTracking(trackingNumber).then(
      (data) => {
        if (active) {
          setLoaded({ key, state: data ? { status: 'found', data } : { status: 'not-found' } });
        }
      },
      (err: unknown) => {
        if (!active) return;
        const state: TrackingState =
          err instanceof TrackingRateLimitError
            ? { status: 'rate-limited', retryAfterSeconds: err.retryAfterSeconds }
            : { status: 'error', message: getErrorMessage(err) };
        setLoaded({ key, state });
      },
    );
    return () => {
      active = false;
    };
  }, [trackingNumber, key]);

  // Anything loaded for a previous tracking number/attempt counts as still loading.
  const state: TrackingState = loaded?.key === key ? loaded.state : { status: 'loading' };
  return { ...state, retry: () => setAttempt((n) => n + 1) };
}
