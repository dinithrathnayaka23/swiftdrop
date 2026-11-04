import { useEffect, useRef, useState } from 'react';
import { getErrorMessage } from '../../api/errors';

export type ApiDataState<T> =
  { status: 'loading' } | { status: 'success'; data: T } | { status: 'error'; message: string };

/**
 * Loads data with {@code load} whenever {@code key} changes (put every input of the request in the
 * key). Results of an older key are ignored. {@code retry()} loads again with the same key.
 */
export function useApiData<T>(
  key: string,
  load: () => Promise<T>,
): ApiDataState<T> & { retry: () => void } {
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<{ key: string; state: ApiDataState<T> } | null>(null);
  const fullKey = `${key}#${attempt}`;
  // The latest load function, so callers can pass an inline arrow without re-running the effect.
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });

  useEffect(() => {
    let active = true;
    loadRef.current().then(
      (data) => {
        if (active) setLoaded({ key: fullKey, state: { status: 'success', data } });
      },
      (err: unknown) => {
        if (active)
          setLoaded({ key: fullKey, state: { status: 'error', message: getErrorMessage(err) } });
      },
    );
    return () => {
      active = false;
    };
  }, [fullKey]);

  const state: ApiDataState<T> = loaded?.key === fullKey ? loaded.state : { status: 'loading' };
  return { ...state, retry: () => setAttempt((n) => n + 1) };
}
