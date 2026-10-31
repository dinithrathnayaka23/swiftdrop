import { countryFor, DEFAULT_COUNTRY_CODE, nationalDigits, toE164 } from './phone';

describe('phone', () => {
  it('defaults to Sri Lanka', () => {
    expect(DEFAULT_COUNTRY_CODE).toBe('+94');
    expect(countryFor('+94')).toMatchObject({ country: 'Sri Lanka', digits: 9 });
  });

  it('returns undefined for an unsupported country code', () => {
    expect(countryFor('+999')).toBeUndefined();
  });

  it.each([
    ['071 234 5678', '712345678'],
    ['071-234-5678', '712345678'],
    ['(071) 2345678', '712345678'],
    ['712345678', '712345678'],
  ])('nationalDigits("%s") strips separators and the leading 0 → %s', (input, expected) => {
    expect(nationalDigits(input)).toBe(expected);
  });

  it('builds the E.164 number the backend stores', () => {
    expect(toE164('+94', '071 234 5678')).toBe('+94712345678');
    expect(toE164('+44', '07400 123456')).toBe('+447400123456');
  });
});
