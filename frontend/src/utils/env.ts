/**
 * The only place that reads Vite's build-time variables (import.meta.env). Everything else imports
 * from here, which also lets Jest swap in fixed values (src/test/env.ts).
 */

/** Backend base URL. Defaults to /api/v1 on the same origin (nginx or the Vite dev proxy). */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api/v1';

/** True in `vite dev`, false in production builds. */
export const IS_DEV: boolean = import.meta.env.DEV;

/**
 * Use sample tracking data in the browser instead of the tracking API, which isn't built yet.
 * Controlled by VITE_USE_MOCK_API ("true"/"false"); defaults to on in local development and off
 * in production builds. Authentication always uses the real backend.
 */
export function isMockApiEnabled(): boolean {
  const flag = import.meta.env.VITE_USE_MOCK_API;
  return flag ? flag === 'true' : IS_DEV;
}
