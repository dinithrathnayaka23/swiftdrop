import { formatPhone, splitPhone } from './phoneParts';

test('splits a stored number into country code and national number', () => {
  expect(splitPhone('+94771234567')).toEqual({ countryCode: '+94', number: '771234567' });
  expect(splitPhone('+447400123456')).toEqual({ countryCode: '+44', number: '7400123456' });
});

test('the longest matching country code wins', () => {
  expect(splitPhone('+9607771234')).toEqual({ countryCode: '+960', number: '7771234' });
});

test('no number starts empty with the default country', () => {
  expect(splitPhone(null)).toEqual({ countryCode: '+94', number: '' });
});

test('formats Sri Lankan numbers in groups', () => {
  expect(formatPhone('+94771234567')).toBe('+94 77 123 4567');
  expect(formatPhone('+447400123456')).toBe('+44 7400123456');
  expect(formatPhone(null)).toBe('Not added');
});
