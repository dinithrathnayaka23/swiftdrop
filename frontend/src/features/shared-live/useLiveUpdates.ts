import { useEffect, useRef, useState } from 'react';
import { useSettings } from '../../hooks/useSettings';
import { openEventStream, StreamHttpError } from './eventStream';
import { LIVE_STATUS_EVENT, type LiveConnectionState, type LiveStatusUpdate } from './types';

/** Waits before reconnecting: 1 s, 2 s, 4 s … up to 30 s. */
export const RECONNECT_DELAYS_MS = [1_000, 2_000, 4_000, 8_000, 16_000, 30_000];

/** Failed attempts in a row before giving up ("offline") until the page is opened again. */
export const MAX_FAILED_ATTEMPTS = 6;

export interface LiveUpdatesOptions {
  /** Stream path relative to the API base, e.g. `/public/tracking/SD1/stream`; null = off. */
  path: string | null;
  /** Sent as a bearer token (admin streams). */
  token?: string | null;
  /**
   * The newest status version per tracking number the screen shows already (from REST). Updates
   * that aren't newer are ignored.
   */
  knownVersions?: Record<string, number>;
  /** A newer update arrived. */
  onUpdate: (update: LiveStatusUpdate) => void;
  /**
   * The stream reconnected after a drop: updates may have been missed, so reload the current
   * state over REST.
   */
  onReconnect?: () => void;
}

/**
 * Follows a live status stream: reconnects with backoff when it drops, skips stale updates, and
 * stops when there is no point retrying (unknown parcel, signed out, maintenance).
 *
 * Pushed updates only keep the screen fresh; the REST API is the true record.
 */
export function useLiveUpdates(options: LiveUpdatesOptions): LiveConnectionState {
  const { path, token } = options;
  const { refresh: refreshSettings } = useSettings();
  const [connection, setConnection] = useState<{ path: string; state: LiveConnectionState }>();

  // Latest callbacks and versions, read by the running stream without restarting it.
  const latest = useRef(options);
  useEffect(() => {
    latest.current = options;
  });
  const versions = useRef(new Map<string, number>());

  useEffect(() => {
    if (!path) return undefined;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let failures = 0;
    let opened = false;
    versions.current = new Map();

    const report = (state: LiveConnectionState) => setConnection({ path, state });

    const isNewer = (update: LiveStatusUpdate) => {
      const known = Math.max(
        versions.current.get(update.trackingNumber) ?? 0,
        latest.current.knownVersions?.[update.trackingNumber] ?? 0,
      );
      if (update.statusVersion <= known) return false;
      versions.current.set(update.trackingNumber, update.statusVersion);
      return true;
    };

    const retryLater = (delayMs?: number) => {
      failures += 1;
      if (failures >= MAX_FAILED_ATTEMPTS) {
        report('offline');
        return;
      }
      report('connecting');
      const wait =
        delayMs ?? RECONNECT_DELAYS_MS[Math.min(failures - 1, RECONNECT_DELAYS_MS.length - 1)];
      timer = setTimeout(connect, wait);
    };

    function connect() {
      openEventStream(path!, {
        signal: controller.signal,
        token,
        onOpen: () => {
          failures = 0;
          report('live');
          if (opened) latest.current.onReconnect?.();
          opened = true;
        },
        onMessage: (message) => {
          if (message.event !== LIVE_STATUS_EVENT) return;
          let update: LiveStatusUpdate;
          try {
            update = JSON.parse(message.data) as LiveStatusUpdate;
          } catch {
            return; // a malformed event is skipped, the stream carries on
          }
          if (isNewer(update)) latest.current.onUpdate(update);
        },
      }).then(
        // The server ended the stream normally (timeout or restart): reconnect straight away.
        () => {
          if (!controller.signal.aborted) {
            report('connecting');
            timer = setTimeout(connect, RECONNECT_DELAYS_MS[0]);
          }
        },
        (err: unknown) => {
          if (controller.signal.aborted) return;
          if (err instanceof StreamHttpError) {
            if (err.code === 'MAINTENANCE_MODE') {
              // Let the app show its maintenance page (it reads the public settings).
              report('offline');
              void refreshSettings();
              return;
            }
            if (err.status === 429) {
              retryLater((err.retryAfterSeconds ?? 30) * 1000);
              return;
            }
            if (err.status === 401 || err.status === 403 || err.status === 404) {
              report('offline'); // retrying can't help
              return;
            }
          }
          retryLater(); // network error or server error
        },
      );
    }

    connect();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [path, token, refreshSettings]);

  if (!path) return 'offline';
  return connection?.path === path ? connection.state : 'connecting';
}
