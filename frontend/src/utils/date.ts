const DATE_TIME = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
});

const SHORT_DATE_TIME = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
});

/** "24 Aug 2025, 11:20 AM" */
export function formatDateTime(iso: string): string {
  return DATE_TIME.format(new Date(iso)).replace(/\b(am|pm)\b/, (m) => m.toUpperCase());
}

/** "24 Aug, 11:20 AM" */
export function formatShortDateTime(iso: string): string {
  return SHORT_DATE_TIME.format(new Date(iso)).replace(/\b(am|pm)\b/, (m) => m.toUpperCase());
}
