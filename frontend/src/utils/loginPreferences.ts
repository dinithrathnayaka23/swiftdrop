/**
 * What the login form remembers on this device when "Remember me" was ticked: the checkbox itself
 * and the email address, so the next sign-in only needs the password. Never the password.
 * Kept in localStorage (survives sign-out and browser restarts); every access is guarded because
 * storage can be blocked.
 */

const KEY = 'swiftdrop.login';

export interface LoginPreferences {
  rememberMe: boolean;
  email: string;
}

export function loadLoginPreferences(): LoginPreferences | null {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<LoginPreferences>;
    return typeof parsed.email === 'string' ? { rememberMe: true, email: parsed.email } : null;
  } catch {
    return null;
  }
}

/** Called after a successful sign-in: remember when "Remember me" was ticked, forget otherwise. */
export function updateLoginPreferences(rememberMe: boolean, email: string): void {
  try {
    if (rememberMe) {
      window.localStorage.setItem(KEY, JSON.stringify({ rememberMe: true, email }));
    } else {
      window.localStorage.removeItem(KEY);
    }
  } catch {
    // ignore: the form just starts empty next time
  }
}
