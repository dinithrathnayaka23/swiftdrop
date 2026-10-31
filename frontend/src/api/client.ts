import axios, { AxiosError, isAxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { AuthResponse, AuthSession } from '../types/auth';
import { API_BASE_URL } from '../utils/env';
import { getToken, saveSession } from '../utils/sessionStorage';

/**
 * Shared Axios instance. Every module's API file should import this rather than
 * calling axios directly, so auth headers, token renewal and error handling stay consistent.
 */
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  // Sends the httpOnly session cookie to /auth/refresh and /auth/logout, also when the API is on
  // another origin. (The cookie is scoped to /api/v1/auth, so no other request carries it.)
  withCredentials: true,
  timeout: 15_000,
});

apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let unauthorizedHandler: (() => void) | null = null;
let sessionUpdatedHandler: ((session: AuthSession) => void) | null = null;

/** Registered by AuthContext: called when the session can't be renewed (sign the user out). */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

/** Registered by AuthContext: called after the access token was renewed in the background. */
export function setSessionUpdatedHandler(handler: ((session: AuthSession) => void) | null): void {
  sessionUpdatedHandler = handler;
}

/**
 * - renewed: new access token (and a new session cookie);
 * - expired: the session is over (signed out, expired, password changed): sign in again;
 * - unavailable: the server couldn't be reached or failed; the session may still be fine.
 */
export type RefreshResult =
  { status: 'renewed'; session: AuthSession } | { status: 'expired' } | { status: 'unavailable' };

async function requestRefresh(): Promise<RefreshResult> {
  try {
    const { data } = await apiClient.post<AuthResponse>('/auth/refresh');
    const session: AuthSession = { token: data.token, user: data.user };
    saveSession(session, data.rememberMe);
    sessionUpdatedHandler?.(session);
    return { status: 'renewed', session };
  } catch (err) {
    const status = isAxiosError(err) ? err.response?.status : undefined;
    // 401: no valid session. 403: the account can no longer sign in (e.g. suspended).
    return status === 401 || status === 403 ? { status: 'expired' } : { status: 'unavailable' };
  }
}

// Several requests can fail with 401 at the same moment; they all wait for one refresh call.
let refreshInFlight: Promise<RefreshResult> | null = null;

/** Renews the access token with the session cookie (one call at a time). */
export function refreshSession(): Promise<RefreshResult> {
  refreshInFlight ??= requestRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

type RetriableRequest = InternalAxiosRequestConfig & { _retried?: boolean };

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as RetriableRequest | undefined;
    // Only a rejected *token* means the access token expired. A 401 from /auth/** (e.g. a wrong
    // password on the login form) is handled by the page itself.
    const sentToken = Boolean(request?.headers?.Authorization);
    const isAuthEndpoint = request?.url?.startsWith('/auth/') ?? false;

    if (error.response?.status === 401 && sentToken && !isAuthEndpoint && request) {
      if (!request._retried) {
        request._retried = true;
        const result = await refreshSession();
        if (result.status === 'renewed') {
          request.headers.Authorization = `Bearer ${result.session.token}`;
          return apiClient(request); // retry the original request once, transparently
        }
        // Server unreachable: keep the user signed in; the page shows the request's own error.
        if (result.status === 'unavailable') return Promise.reject(error);
      }
      unauthorizedHandler?.();
    }
    return Promise.reject(error);
  },
);
