import { authApi } from '../../api/auth';
import { apiClient } from '../../api/client';
import { request } from '../../api/errors';
import type { UpdateProfileRequest, UserProfile } from './types';

/** The signed-in user's own profile (any role). */
export const profileApi = {
  get: () => request(() => apiClient.get<UserProfile>('/users/me')),

  /** 400 with per-field errors when the name or phone number is invalid. */
  update: (body: UpdateProfileRequest) =>
    request(() => apiClient.patch<UserProfile>('/users/me', body)),

  /** Emails a password reset link to the user's own address (the existing reset flow). */
  sendPasswordResetLink: (email: string) => authApi.requestPasswordReset({ email }),
};
