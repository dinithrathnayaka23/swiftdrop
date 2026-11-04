import { formatDate, formatDateTime, formatShortDateTime, type DateDisplay } from './date';

// 24 Aug 2025, 05:50 UTC = 11:20 in Colombo (UTC+05:30).
const ISO = '2025-08-24T05:50:00Z';

function display(overrides: Partial<DateDisplay>): DateDisplay {
  return { timeZone: 'Asia/Colombo', dateFormat: 'DD_MMM_YYYY', timeFormat: 'H12', ...overrides };
}

describe('date formatting', () => {
  it('defaults to the design style in Sri Lanka time', () => {
    expect(formatDateTime(ISO)).toBe('24 Aug 2025, 11:20 AM');
    expect(formatShortDateTime(ISO)).toBe('24 Aug, 11:20 AM');
    expect(formatDate(ISO)).toBe('24 Aug 2025');
  });

  it.each([
    ['DD_MM_YYYY', '24/08/2025, 11:20 AM'],
    ['MM_DD_YYYY', '08/24/2025, 11:20 AM'],
    ['YYYY_MM_DD', '2025-08-24, 11:20 AM'],
  ] as const)('uses the %s date format', (dateFormat, expected) => {
    expect(formatDateTime(ISO, display({ dateFormat }))).toBe(expected);
  });

  it('uses 24-hour time when chosen', () => {
    expect(formatDateTime('2025-08-24T09:00:00Z', display({ timeFormat: 'H24' }))).toBe(
      '24 Aug 2025, 14:30',
    );
  });

  it('converts to the chosen time zone', () => {
    expect(formatDateTime(ISO, display({ timeZone: 'UTC' }))).toBe('24 Aug 2025, 05:50 AM');
  });

  it('falls back to Sri Lanka time for a time zone the browser does not know', () => {
    expect(formatDateTime(ISO, display({ timeZone: 'Mars/Olympus' }))).toBe(
      '24 Aug 2025, 11:20 AM',
    );
  });
});
