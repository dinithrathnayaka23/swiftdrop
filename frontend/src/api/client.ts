import axios, { AxiosError } from 'axios';
import { getToken } from '../utils/sessionStorage';

/**
 * Shared Axios instance. Every module's API file should import this rather than
 * calling axios directly, so auth headers and error handling stay consistent.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: { 'Content-Type': 'application/json' },
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

/** Registered by AuthContext so a 401 anywhere logs the user out. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      unauthorizedHandler?.();
    }
    return Promise.reject(error);
  },
);
