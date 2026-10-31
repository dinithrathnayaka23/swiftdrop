import { getPasswordStrength, passwordRequirements } from './passwordStrength';
import { validateNewPassword } from './validation';

describe('getPasswordStrength', () => {
  it.each([
    ['', 0, ''],
    ['abc1', 0, 'Too short'],
    ['parcel26', 1, 'Weak'], // 8 chars, letters + digits only
    ['parcelbox2026', 2, 'Fair'], // 12+ chars
    ['ParcelBox2026', 3, 'Good'], // + mixed case
    ['ParcelBox#2026', 4, 'Strong'], // + symbol
  ])('rates %j as level %i (%s)', (password, level, label) => {
    expect(getPasswordStrength(password)).toMatchObject({ level, label });
  });

  it('flags very common passwords as weak with a tip', () => {
    const strength = getPasswordStrength('Password123');
    expect(strength.level).toBe(1);
    expect(strength.tip).toMatch(/very common/);
  });

  it('flags repeated characters', () => {
    expect(getPasswordStrength('aaaa1111bbbb').tip).toMatch(/repeated/);
  });

  it('suggests the next improvement', () => {
    expect(getPasswordStrength('parcel26').tip).toMatch(/12 or more/);
    expect(getPasswordStrength('parcelbox2026').tip).toMatch(/upper- and lower-case/);
    expect(getPasswordStrength('ParcelBox2026').tip).toMatch(/symbol/);
    expect(getPasswordStrength('ParcelBox#2026').tip).toBeUndefined();
  });
});

describe('passwordRequirements', () => {
  it('ticks each rule as it is met', () => {
    expect(passwordRequirements('').map((r) => r.met)).toEqual([false, false, false, false]);
    expect(passwordRequirements('abc').map((r) => r.met)).toEqual([false, true, false, true]);
    expect(passwordRequirements(' parcel26').map((r) => r.met)).toEqual([true, true, true, false]);
    expect(passwordRequirements('parcel26').map((r) => r.met)).toEqual([true, true, true, true]);
  });

  it.each(['parcel26', 'abc', ' parcel26', '12345678', 'abcdefgh'])(
    'agrees with validateNewPassword for %j',
    (password) => {
      const allMet = passwordRequirements(password).every((r) => r.met);
      expect(allMet).toBe(validateNewPassword(password) === undefined);
    },
  );
});
