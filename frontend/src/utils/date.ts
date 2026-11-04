import type { DateFormat, TimeFormat } from '../types/settings';

/** How dates and times are shown; set by the admin in System Settings. */
export interface DateDisplay {
  timeZone: string;
  dateFormat: DateFormat;
  timeFormat: TimeFormat;
}

export const DEFAULT_DATE_DISPLAY: DateDisplay = {
  timeZone: 'Asia/Colombo',
  dateFormat: 'DD_MMM_YYYY',
  timeFormat: 'H12',
};

type Parts = Record<
  'day' | 'month' | 'monthName' | 'year' | 'hour' | 'minute' | 'dayPeriod',
  string
>;

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(timeZone: string, hour12: boolean): Intl.DateTimeFormat {
  const key = `${timeZone}|${hour12}`;
  let f = formatters.get(key);
  if (!f) {
    f = new Intl.DateTimeFormat('en-GB', {
      timeZone,
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12,
    });
    formatters.set(key, f);
  }
  return f;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function parts(iso: string, display: DateDisplay): Parts {
  const hour12 = display.timeFormat === 'H12';
  let raw: Intl.DateTimeFormatPart[];
  try {
    raw = formatter(display.timeZone, hour12).formatToParts(new Date(iso));
  } catch {
    // Unknown time zone in this browser: fall back to the default rather than failing the page.
    raw = formatter(DEFAULT_DATE_DISPLAY.timeZone, hour12).formatToParts(new Date(iso));
  }
  const get = (type: Intl.DateTimeFormatPartTypes) => raw.find((p) => p.type === type)?.value ?? '';
  const month = get('month');
  return {
    day: get('day'),
    month,
    monthName: MONTHS[Number(month) - 1] ?? month,
    year: get('year'),
    // Some engines write midnight as "24" in 24-hour time.
    hour: hour12 ? get('hour') : get('hour').replace(/^24$/, '00'),
    minute: get('minute'),
    dayPeriod: get('dayPeriod').toUpperCase(),
  };
}

function datePart(p: Parts, format: DateFormat, withYear: boolean): string {
  switch (format) {
    case 'DD_MM_YYYY':
      return withYear ? `${p.day}/${p.month}/${p.year}` : `${p.day}/${p.month}`;
    case 'MM_DD_YYYY':
      return withYear ? `${p.month}/${p.day}/${p.year}` : `${p.month}/${p.day}`;
    case 'YYYY_MM_DD':
      return withYear ? `${p.year}-${p.month}-${p.day}` : `${p.month}-${p.day}`;
    case 'DD_MMM_YYYY':
    default:
      return withYear ? `${p.day} ${p.monthName} ${p.year}` : `${p.day} ${p.monthName}`;
  }
}

function timePart(p: Parts, format: TimeFormat): string {
  return format === 'H12' ? `${p.hour}:${p.minute} ${p.dayPeriod}` : `${p.hour}:${p.minute}`;
}

/** "24 Aug 2025, 11:20 AM" (or the admin's chosen format). */
export function formatDateTime(iso: string, display: DateDisplay = DEFAULT_DATE_DISPLAY): string {
  const p = parts(iso, display);
  return `${datePart(p, display.dateFormat, true)}, ${timePart(p, display.timeFormat)}`;
}

/** "24 Aug, 11:20 AM": no year, for timelines. */
export function formatShortDateTime(
  iso: string,
  display: DateDisplay = DEFAULT_DATE_DISPLAY,
): string {
  const p = parts(iso, display);
  return `${datePart(p, display.dateFormat, false)}, ${timePart(p, display.timeFormat)}`;
}

/** "24 Aug 2025": date only. */
export function formatDate(iso: string, display: DateDisplay = DEFAULT_DATE_DISPLAY): string {
  return datePart(parts(iso, display), display.dateFormat, true);
}
