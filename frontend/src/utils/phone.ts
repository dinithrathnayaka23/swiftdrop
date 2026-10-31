/** Supported phone country codes and the length of a national number (without the leading 0). */
export const COUNTRY_CODES = [
  { code: '+94', iso: 'LK', country: 'Sri Lanka', digits: 9, example: '71 234 5678' },
  { code: '+91', iso: 'IN', country: 'India', digits: 10, example: '98765 43210' },
  { code: '+960', iso: 'MV', country: 'Maldives', digits: 7, example: '777 1234' },
  { code: '+44', iso: 'GB', country: 'United Kingdom', digits: 10, example: '7400 123456' },
  { code: '+1', iso: 'US', country: 'United States', digits: 10, example: '201 555 0123' },
] as const;

export type CountryCode = (typeof COUNTRY_CODES)[number]['code'];

export const DEFAULT_COUNTRY_CODE: CountryCode = '+94';

export function countryFor(code: string) {
  return COUNTRY_CODES.find((c) => c.code === code);
}

/** Digits only, without separators or the local leading 0: "071 234-5678" → "712345678". */
export function nationalDigits(number: string): string {
  return number.replace(/[\s()-]/g, '').replace(/^0+/, '');
}

/** "+94" + "071 234 5678" → "+94712345678" */
export function toE164(countryCode: string, number: string): string {
  return countryCode + nationalDigits(number);
}
