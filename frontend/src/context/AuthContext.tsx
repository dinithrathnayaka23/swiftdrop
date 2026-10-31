import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { authApi } from '../api/auth';
import {
  refreshSession,
  setSessionUpdatedHandler,
  setUnauthorizedHandler,
  type RefreshResult,
} from '../api/client';
import { PATHS, ROLE_SIGN_IN } from '../routes/paths';
import type { AuthSession } from '../types/auth';
import {
  clearSession,
  loadSession,
  loadStoredUser,
  saveSession,
  SESSION_HINT_KEY,
} from '../utils/sessionStorage';
import {
  AuthContext,
  type AuthContextValue,
  type AuthStatus,
  type LoginOptions,
} from './authContextValue';

/** A user from an earlier visit, but no access token in this page yet: renew via the cookie. */
function initialStatus(): AuthStatus {
  return !loadSession() && loadStoredUser() ? 'restoring' : 'ready';
}

/**
 * Holds the current session. Login and sign-up pages call `login()` with the access token and
 * user returned by the backend; the refresh token is an httpOnly cookie the browser handles.
 * After a reload the session is restored with that cookie; expired access tokens are renewed in
 * the background by the API client. Must be rendered inside the router.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => loadSession());
  const [status, setStatus] = useState<AuthStatus>(initialStatus);
  const navigate = useNavigate();

  const login = useCallback((next: AuthSession, options?: LoginOptions) => {
    saveSession(next, options?.remember ?? false);
    setSession(next);
    setStatus('ready');
  }, []);

  const endLocally = useCallback(() => {
    clearSession();
    setSession(null);
    setStatus('ready');
  }, []);

  const logout = useCallback(async () => {
    endLocally();
    try {
      await authApi.logout();
    } catch {
      // Offline or server error: the cookie and server session still expire on their own.
    }
  }, [endLocally]);

  const applyRestore = useCallback(
    (result: RefreshResult) => {
      if (result.status === 'renewed') {
        setSession(result.session);
        setStatus('ready');
      } else if (result.status === 'expired') {
        endLocally();
      } else {
        setStatus('offline'); // keep the stored user so "Try again" can still restore it
      }
    },
    [endLocally],
  );

  const retryRestore = useCallback(() => {
    setStatus('restoring');
    void refreshSession().then(applyRestore);
  }, [applyRestore]);

  // After a reload: renew the session with the cookie once.
  useEffect(() => {
    if (initialStatus() !== 'restoring') return;
    let active = true;
    void refreshSession().then((result) => {
      if (active) applyRestore(result);
    });
    return () => {
      active = false;
    };
  }, [applyRestore]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      // Send staff back to their own sign-in page afterwards.
      const role = loadStoredUser()?.role;
      endLocally();
      navigate(PATHS.sessionExpired, {
        replace: true,
        state: { signInPath: role ? ROLE_SIGN_IN[role] : PATHS.login },
      });
    });
    setSessionUpdatedHandler(setSession);
    return () => {
      setUnauthorizedHandler(null);
      setSessionUpdatedHandler(null);
    };
  }, [endLocally, navigate]);

  // Signing out in one tab signs out the others ("Remember me" sessions share localStorage).
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === SESSION_HINT_KEY && event.newValue === null) endLocally();
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [endLocally]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      token: session?.token ?? null,
      isAuthenticated: session !== null,
      status,
      login,
      logout,
      retryRestore,
    }),
    [session, status, login, logout, retryRestore],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
