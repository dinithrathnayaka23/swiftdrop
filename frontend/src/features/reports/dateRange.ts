import type { DateRange } from './types';

/** Longest range the backend accepts in one request. */
export const MAX_RANGE_DAYS = 366;

/** yyyy-MM-dd for a local date. */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** A yyyy-MM-dd string as a local date at midnight. */
export function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/** The last {@code days} days, ending today. */
export function lastDays(days: number, today = new Date()): DateRange {
  const from = new Date(today);
  from.setDate(today.getDate() - (days - 1));
  return { from: toIsoDate(from), to: toIsoDate(today) };
}

/** From the 1st of this month to today. */
export function thisMonth(today = new Date()): DateRange {
  return {
    from: toIsoDate(new Date(today.getFullYear(), today.getMonth(), 1)),
    to: toIsoDate(today),
  };
}

/** Days in the range, counting both ends. */
export function daysIn(range: DateRange): number {
  const ms = parseIsoDate(range.to).getTime() - parseIsoDate(range.from).getTime();
  return Math.round(ms / 86_400_000) + 1;
}

/**
 * Why the range can't be used, or null when it's fine. Mirrors the backend's checks so the admin
 * sees the problem before a request is sent.
 */
export function rangeError(range: DateRange, today = new Date()): string | null {
  if (!range.from || !range.to) return 'Choose both a start and an end date.';
  if (range.from > range.to) return 'The start date must be on or before the end date.';
  if (range.from > toIsoDate(today)) return 'The start date cannot be in the future.';
  if (daysIn(range) > MAX_RANGE_DAYS) return `Choose a range of at most ${MAX_RANGE_DAYS} days.`;
  return null;
}
