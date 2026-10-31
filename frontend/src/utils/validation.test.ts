import { toE164 } from './phone';
import {
  normalizeRegistrationNo,
  validateAddress,
  validateEmail,
  validateNewPassword,
  validatePersonName,
  validatePhone,
  validateRegistrationNo,
  validateTrackingNumber,
} from './validation';

describe('validateEmail', () => {
  it.each(['', '   '])('requires a value (%j)', (v) => {
    expect(validateEmail(v)).toBe('Email is required');
  });
  it.each(['plainaddress', 'a@b', 'a@b.', '@example.com', 'a b@example.com', 'a@exa mple.com'])(
    'rejects %s',
    (v) => expect(validateEmail(v)).toBe('Enter a valid email address'),
  );
  it.each(['jane@example.com', 'jane.doe+parcels@mail.example.lk', '  jane@example.com  '])(
    'accepts %s',
    (v) => expect(validateEmail(v)).toBeUndefined(),
  );
  it('enforces the 255 character column limit', () => {
    expect(validateEmail(`${'a'.repeat(250)}@x.com`)).toMatch(/at most 255/);
  });
});

describe('validatePersonName', () => {
  it.each(['Jane Doe', "Mary-Ann O'Neil", 'Dr. Kamal Perera', 'නිමල් පෙරේරා'])('accepts %s', (v) =>
    expect(validatePersonName(v)).toBeUndefined(),
  );
  it('requires at least 2 characters', () => {
    expect(validatePersonName('J')).toMatch(/at least 2/);
  });
  it.each(['Jane123', 'Jane@Doe', '-Jane'])('rejects %s', (v) =>
    expect(validatePersonName(v)).toMatch(/can only contain letters/),
  );
  it('uses the given label', () => {
    expect(validatePersonName('', 'Contact person name')).toBe('Contact person name is required');
  });
});

describe('validateNewPassword', () => {
  it.each([
    ['', 'Password is required'],
    ['abc12', 'Password must be at least 8 characters'],
    ['abcdefgh', 'Password must include at least one letter and one number'],
    ['12345678', 'Password must include at least one letter and one number'],
    [' secret123', 'Password cannot start or end with a space'],
    [`a1${'x'.repeat(71)}`, 'Password must be at most 72 characters'],
  ])('rejects %j', (v, message) => expect(validateNewPassword(v)).toBe(message));

  it('accepts a password with letters and numbers', () => {
    expect(validateNewPassword('parcel2026')).toBeUndefined();
  });
});

describe('validatePhone', () => {
  it.each(['712345678', '0712345678', '071 234 5678', '071-234-5678'])(
    'accepts Sri Lankan number %s',
    (v) => expect(validatePhone('+94', v)).toBeUndefined(),
  );
  it('requires a value', () => {
    expect(validatePhone('+94', '')).toBe('Phone number is required');
  });
  it('rejects letters', () => {
    expect(validatePhone('+94', '71abc5678')).toBe('Phone number can only contain digits');
  });
  it('checks the length for the selected country', () => {
    expect(validatePhone('+94', '71234567')).toMatch(/Sri Lanka number \(9 digits/);
    expect(validatePhone('+91', '9876543210')).toBeUndefined();
    expect(validatePhone('+960', '7771234')).toBeUndefined();
  });
  it('formats as E.164', () => {
    expect(toE164('+94', '071 234 5678')).toBe('+94712345678');
  });
});

describe('merchant fields', () => {
  it.each(['PV00234567', 'PB12345678', 'GA00000001', 'pv00234567', 'PV 00234567', 'PV-00234567'])(
    'accepts registration number %j',
    (v) => expect(validateRegistrationNo(v)).toBeUndefined(),
  );

  it.each([
    'PV0023456', // 7 digits
    'PV002345678', // 9 digits
    'PX00234567', // unknown prefix
    'FC00234567', // not PV/PB/GA
    '00234567',
    'PV0023456A',
    'PV#00234567',
  ])('rejects registration number %j', (v) =>
    expect(validateRegistrationNo(v)).toMatch(/PV, PB or GA followed by 8 digits/),
  );

  it('requires a registration number', () => {
    expect(validateRegistrationNo('  ')).toBe('Registration number is required');
  });

  it('normalises to the stored form', () => {
    expect(normalizeRegistrationNo(' pv 0023-4567 ')).toBe('PV00234567');
  });
  it('requires a full business address', () => {
    expect(validateAddress('Colombo')).toBe('Please enter the full business address');
    expect(validateAddress('12 Main Street, Colombo 07')).toBeUndefined();
  });
});

describe('validateTrackingNumber', () => {
  it.each([
    ['', 'Please enter a tracking number'],
    ['SD 123', 'Tracking numbers contain only letters, numbers and hyphens'],
    ['SD12', 'Enter a valid tracking number'],
  ])('rejects %j', (v, message) => expect(validateTrackingNumber(v)).toBe(message));

  it('accepts a normal tracking number', () => {
    expect(validateTrackingNumber('SD123456789')).toBeUndefined();
  });
});
