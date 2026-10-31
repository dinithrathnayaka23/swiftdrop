import { useEffect, useState } from 'react';
import { getErrorMessage } from '../api/errors';
import { trackingApi } from '../api/tracking';
import type { TrackingResult } from '../types/tracking';

export type TrackingState =
  | { status: 'loading' }
  | { status: 'found'; data: TrackingResult }
  | { status: 'not-found' }
  | { status: 'error'; message: string };

/** Loads public tracking info; `retry()` reloads after an error. */
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
        if (active) setLoaded({ key, state: { status: 'error', message: getErrorMessage(err) } });
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
