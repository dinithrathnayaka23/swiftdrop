import type { AuthSession } from '../types/auth';

const KEY = 'swiftdrop.session';

// localStorage can throw (private mode, blocked storage), so every access is guarded.

export function loadSession(): AuthSession | null {
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as AuthSession) : null;
  } catch {
    return null;
  }
}

export function saveSession(session: AuthSession): void {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    // ignore: the session simply won't survive a reload
  }
}

export function clearSession(): void {
  try {
    window.localStorage.removeItem(KEY);
  } catch {
    // ignore
  }
}

export function getToken(): string | null {
  return loadSession()?.token ?? null;
}
