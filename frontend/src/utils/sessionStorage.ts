import type { AuthSession, AuthUser } from '../types/auth';

/**
 * Where the signed-in session lives in the browser:
 * - Access token: in memory only. Never written to storage, so a malicious script can't lift it
 *   from there; it is short-lived and renewed silently.
 * - Refresh token: an httpOnly cookie set by the backend. JavaScript can't read it at all.
 * - User (id, name, role, no secrets): stored as a hint that a session can be restored after a
 *   reload. localStorage with "Remember me" (survives restarts), sessionStorage otherwise.
 * Storage can throw (private mode, blocked storage), so every access is guarded.
 */

/** Storage key of the signed-in user. Other tabs watch it to sign out together. */
export const SESSION_HINT_KEY = 'swiftdrop.user';
/** Earlier versions stored tokens under this key; they are removed on sight. */
const LEGACY_KEY = 'swiftdrop.session';

let accessToken: string | null = null;

function storages(): Storage[] {
  try {
    return [window.localStorage, window.sessionStorage];
  } catch {
    return [];
  }
}

function read(storage: Storage): AuthUser | null {
  try {
    storage.removeItem(LEGACY_KEY);
    const raw = storage.getItem(SESSION_HINT_KEY);
    if (!raw) return null;
    const user = JSON.parse(raw) as Partial<AuthUser>;
    return typeof user.id === 'string' &&
      typeof user.displayName === 'string' &&
      typeof user.role === 'string'
      ? (user as AuthUser)
      : null;
  } catch {
    return null;
  }
}

/** The user from the last sign-in on this device (may need restoring: see AuthContext). */
export function loadStoredUser(): AuthUser | null {
  for (const storage of storages()) {
    const user = read(storage);
    if (user) return user;
  }
  return null;
}

/** The current session, if this page has an access token. */
export function loadSession(): AuthSession | null {
  const user = loadStoredUser();
  return accessToken && user ? { token: accessToken, user } : null;
}

export function saveSession(session: AuthSession, remember: boolean): void {
  clearSession();
  accessToken = session.token;
  try {
    (remember ? window.localStorage : window.sessionStorage).setItem(
      SESSION_HINT_KEY,
      JSON.stringify(session.user),
    );
  } catch {
    // ignore: the session simply won't be restored after a reload
  }
}

export function clearSession(): void {
  accessToken = null;
  for (const storage of storages()) {
    try {
      storage.removeItem(SESSION_HINT_KEY);
      storage.removeItem(LEGACY_KEY);
    } catch {
      // ignore
    }
  }
}

export function getToken(): string | null {
  return accessToken;
}
