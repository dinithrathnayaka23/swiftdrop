import { createContext } from 'react';
import type { AuthSession, AuthUser } from '../types/auth';

export interface LoginOptions {
  /** Keep the session after the browser closes ("Remember me"). Defaults to false. */
  remember?: boolean;
}

/**
 * - restoring: a previous session is being renewed with the session cookie (after a reload);
 * - ready: signed in or not, the answer is known;
 * - offline: a previous session couldn't be checked because the server is unreachable.
 */
export type AuthStatus = 'restoring' | 'ready' | 'offline';

export interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  status: AuthStatus;
  login: (session: AuthSession, options?: LoginOptions) => void;
  /** Signs out here and on the server (this device's session ends immediately). */
  logout: () => Promise<void>;
  /** Tries again to restore the session (after "offline"). */
  retryRestore: () => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
