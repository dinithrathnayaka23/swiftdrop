import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router';
import { authApi } from '../api/auth';
import { toApiRequestError } from '../api/errors';
import { ROLE_HOME } from '../routes/paths';
import type { AuthResponse } from '../types/auth';
import { useAuth } from './useAuth';

interface UseGoogleSignInOptions {
  rememberMe: boolean;
  /** Where to go afterwards; defaults to the user's role home. */
  redirectTo?: string;
  onSuccess?: (response: AuthResponse) => void;
  /** `code` is the backend's error code, e.g. WRONG_SIGN_IN_PAGE for a staff account. */
  onError: (message: string | null, code?: string) => void;
}

/**
 * Exchanges the Google ID token from GoogleSignInButton for a SwiftDrop session, then signs in
 * exactly like the email form does. New Google users get a customer account.
 */
export function useGoogleSignIn({
  rememberMe,
  redirectTo,
  onSuccess,
  onError,
}: UseGoogleSignInOptions) {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);

  const signIn = useCallback(
    async (credential: string) => {
      setBusy(true);
      onError(null);
      try {
        const response = await authApi.loginWithGoogle({ credential, rememberMe });
        onSuccess?.(response);
        login({ token: response.token, user: response.user }, { remember: response.rememberMe });
        navigate(redirectTo ?? ROLE_HOME[response.user.role], { replace: true });
      } catch (err) {
        // 403: e.g. a merchant still awaiting approval; the backend's message says so.
        const apiError = toApiRequestError(err);
        onError(apiError.message, apiError.code);
        setBusy(false);
      }
    },
    [rememberMe, redirectTo, onSuccess, onError, login, navigate],
  );

  return { signIn, busy };
}
