import { COUNTRY_CODES, DEFAULT_COUNTRY_CODE } from '../../utils/phone';

/**
 * Splits a stored E.164 number into the country code and national number the phone field edits:
 * "+94771234567" → { countryCode: "+94", number: "771234567" }. Unknown or missing numbers fall
 * back to the default country with the digits as they are.
 */
export function splitPhone(phone: string | null): { countryCode: string; number: string } {
  if (!phone) return { countryCode: DEFAULT_COUNTRY_CODE, number: '' };
  // Longest code first, so "+960" (Maldives) wins over a shorter code that also matches.
  const match = [...COUNTRY_CODES]
    .sort((a, b) => b.code.length - a.code.length)
    .find((c) => phone.startsWith(c.code));
  return match
    ? { countryCode: match.code, number: phone.slice(match.code.length) }
    : { countryCode: DEFAULT_COUNTRY_CODE, number: phone.replace(/^\+/, '') };
}

/** "+94771234567" → "+94 77 123 4567" for display (Sri Lankan grouping; others stay compact). */
export function formatPhone(phone: string | null): string {
  if (!phone) return 'Not added';
  const { countryCode, number } = splitPhone(phone);
  if (countryCode === '+94' && number.length === 9) {
    return `${countryCode} ${number.slice(0, 2)} ${number.slice(2, 5)} ${number.slice(5)}`;
  }
  return `${countryCode} ${number}`;
}
