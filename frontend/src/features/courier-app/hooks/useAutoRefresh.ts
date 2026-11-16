import { useCallback, useEffect, useRef, useState } from 'react';
import { getErrorMessage } from '../../../api/errors';

export type AutoRefreshState<T> =
  | { status: 'loading' }
  /** {@code stale}: the last background refresh failed, so this is the previous data. */
  | { status: 'success'; data: T; stale: boolean }
  | { status: 'error'; message: string };

/** Default background refresh: once a minute. */
export const REFRESH_INTERVAL_MS = 60_000;

/**
 * Loads data and keeps it fresh: again every {@code intervalMs}, and whenever the courier comes
 * back to the app (window focus, or the tab becoming visible on a phone). Background refreshes keep
 * the current data on screen; only the first load shows the loading state.
 */
export function useAutoRefresh<T>(load: () => Promise<T>, intervalMs = REFRESH_INTERVAL_MS) {
  const [state, setState] = useState<AutoRefreshState<T>>({ status: 'loading' });
  // The latest load function, so callers can pass an inline arrow without restarting the timers.
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });
  // Only the newest request may update the state.
  const latestRequest = useRef(0);

  const refresh = useCallback(() => {
    const requestId = ++latestRequest.current;
    loadRef.current().then(
      (data) => {
        if (requestId === latestRequest.current)
          setState({ status: 'success', data, stale: false });
      },
      (err: unknown) => {
        if (requestId !== latestRequest.current) return;
        setState((previous) =>
          previous.status === 'success'
            ? { ...previous, stale: true }
            : { status: 'error', message: getErrorMessage(err) },
        );
      },
    );
  }, []);

  useEffect(() => {
    const requests = latestRequest;
    refresh();
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', onVisible);
    const timer = window.setInterval(refresh, intervalMs);
    return () => {
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', onVisible);
      window.clearInterval(timer);
      requests.current++; // ignore answers that arrive after unmount
    };
  }, [refresh, intervalMs]);

  /** Shows the loading state and loads again (after an error). */
  const retry = useCallback(() => {
    setState({ status: 'loading' });
    refresh();
  }, [refresh]);

  return { state, refresh, retry };
}
