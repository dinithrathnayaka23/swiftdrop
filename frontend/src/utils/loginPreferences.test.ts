import { loadLoginPreferences, updateLoginPreferences } from './loginPreferences';

describe('loginPreferences', () => {
  it('is empty on a new device', () => {
    expect(loadLoginPreferences()).toBeNull();
  });

  it('remembers the email (never a password) after a "Remember me" sign-in', () => {
    updateLoginPreferences(true, 'nimal@example.com');

    expect(loadLoginPreferences()).toEqual({ rememberMe: true, email: 'nimal@example.com' });
    expect(Object.keys(JSON.parse(window.localStorage.getItem('swiftdrop.login') ?? '{}'))).toEqual(
      ['rememberMe', 'email'],
    );
  });

  it('forgets everything after a sign-in without "Remember me"', () => {
    updateLoginPreferences(true, 'nimal@example.com');
    updateLoginPreferences(false, 'nimal@example.com');

    expect(loadLoginPreferences()).toBeNull();
  });

  it('ignores stored data in an unexpected shape', () => {
    window.localStorage.setItem('swiftdrop.login', JSON.stringify({ rememberMe: true }));
    expect(loadLoginPreferences()).toBeNull();

    window.localStorage.setItem('swiftdrop.login', 'not json');
    expect(loadLoginPreferences()).toBeNull();
  });
});
