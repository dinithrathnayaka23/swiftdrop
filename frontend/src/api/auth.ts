import type {
  AuthApi,
  AuthResponse,
  CustomerRegistrationResponse,
  EmailVerifiedResponse,
  GoogleConfig,
  MerchantRegistrationResponse,
} from '../types/auth';
import { apiClient } from './client';
import { request } from './errors';

/** Identity module endpoints (/api/v1/auth/**). */
export const authApi: AuthApi = {
  login: (body) => request(() => apiClient.post<AuthResponse>('/auth/login', body)),
  refresh: () => request(() => apiClient.post<AuthResponse>('/auth/refresh')),
  logout: async () => {
    await request(() => apiClient.post<void>('/auth/logout'));
  },
  googleConfig: () => request(() => apiClient.get<GoogleConfig>('/auth/google/config')),
  loginWithGoogle: (body) => request(() => apiClient.post<AuthResponse>('/auth/google', body)),
  registerCustomer: (body) =>
    request(() => apiClient.post<CustomerRegistrationResponse>('/auth/register/customer', body)),
  confirmEmailVerification: (body) =>
    request(() => apiClient.post<EmailVerifiedResponse>('/auth/verify-email/confirm', body)),
  resendVerification: async (body) => {
    await request(() => apiClient.post<void>('/auth/verify-email/resend', body));
  },
  registerMerchant: (body) =>
    request(() => apiClient.post<MerchantRegistrationResponse>('/auth/register/merchant', body)),
  requestPasswordReset: async (body) => {
    await request(() => apiClient.post<void>('/auth/password-reset', body));
  },
  confirmPasswordReset: async (body) => {
    await request(() => apiClient.post<void>('/auth/password-reset/confirm', body));
  },
};
