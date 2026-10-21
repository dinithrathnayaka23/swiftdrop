import { useEffect, useState } from 'react';
import { getHealth } from '../api/health';

export type ApiHealth = 'checking' | 'up' | 'down';

/** Pings /api/v1/health once; handy to confirm the frontend can reach the backend. */
export function useApiHealth(): ApiHealth {
  const [status, setStatus] = useState<ApiHealth>('checking');

  useEffect(() => {
    let active = true;
    getHealth()
      .then((res) => active && setStatus(res.status === 'UP' ? 'up' : 'down'))
      .catch(() => active && setStatus('down'));
    return () => {
      active = false;
    };
  }, []);

  return status;
}
