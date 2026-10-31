import type { AuthSession } from '../types/auth';
import {
  clearSession,
  getToken,
  loadSession,
  loadStoredUser,
  saveSession,
  SESSION_HINT_KEY,
} from './sessionStorage';

const session: AuthSession = {
  token: 'access-jwt',
  user: { id: '1', displayName: 'Nimal Perera', role: 'CUSTOMER' },
};

describe('sessionStorage', () => {
  it('keeps the access token in memory only, never in browser storage', () => {
    saveSession(session, true);

    expect(getToken()).toBe('access-jwt');
    expect(JSON.stringify({ ...window.localStorage })).not.toContain('access-jwt');
    expect(JSON.stringify({ ...window.sessionStorage })).not.toContain('access-jwt');
  });

  it('stores the user in localStorage with "Remember me" (restored after a restart)', () => {
    saveSession(session, true);

    expect(JSON.parse(window.localStorage.getItem(SESSION_HINT_KEY)!)).toEqual(session.user);
    expect(window.sessionStorage.getItem(SESSION_HINT_KEY)).toBeNull();
    expect(loadSession()).toEqual(session);
  });

  it('stores the user in sessionStorage otherwise (forgotten when the browser closes)', () => {
    saveSession(session, false);

    expect(window.sessionStorage.getItem(SESSION_HINT_KEY)).toContain('Nimal Perera');
    expect(window.localStorage.getItem(SESSION_HINT_KEY)).toBeNull();
  });

  it('after a reload there is a stored user but no access token yet', () => {
    window.localStorage.setItem(SESSION_HINT_KEY, JSON.stringify(session.user));

    expect(loadStoredUser()).toEqual(session.user);
    expect(loadSession()).toBeNull(); // AuthContext restores it with the session cookie
  });

  it('clearSession forgets the token and the user everywhere', () => {
    saveSession(session, true);
    clearSession();

    expect(getToken()).toBeNull();
    expect(loadStoredUser()).toBeNull();
  });

  it('removes tokens stored by earlier versions', () => {
    window.localStorage.setItem('swiftdrop.session', '{"token":"old","refreshToken":"old"}');

    loadStoredUser();

    expect(window.localStorage.getItem('swiftdrop.session')).toBeNull();
  });

  it('ignores corrupted or incomplete stored data', () => {
    window.localStorage.setItem(SESSION_HINT_KEY, '{not json');
    expect(loadStoredUser()).toBeNull();

    window.localStorage.setItem(SESSION_HINT_KEY, JSON.stringify({ id: '1' }));
    expect(loadStoredUser()).toBeNull();
  });

  it('keeps working when storage is blocked (e.g. private mode)', () => {
    const setItem = jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });

    expect(() => saveSession(session, true)).not.toThrow();
    expect(getToken()).toBe('access-jwt'); // still signed in for this page
    setItem.mockRestore();
  });
});
