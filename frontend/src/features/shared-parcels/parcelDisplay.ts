import type { ParcelType, StatusCounts, StatusHistoryItem } from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';
import {
  isFailureStatus,
  sinceLatestCreated,
  STATUS_META,
  type StepState,
} from '../../utils/parcelStatus';

const MONEY = new Intl.NumberFormat('en-LK', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** "Rs. 750.00" (LKR) or "USD 12.00" for another currency. */
export function formatFee(amount: number, currency = 'LKR'): string {
  const prefix = currency === 'LKR' ? 'Rs.' : currency;
  return `${prefix} ${MONEY.format(amount)}`;
}

const DATE = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
const SHORT_DATE = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' });
const TIME = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: true });

function upperMeridiem(text: string): string {
  return text.replace(/\b(am|pm)\b/, (m) => m.toUpperCase());
}

/** "24 Aug 2025" */
export function formatDate(iso: string): string {
  return DATE.format(new Date(iso));
}

/** "24 Aug" */
export function formatShortDate(iso: string): string {
  return SHORT_DATE.format(new Date(iso));
}

/** "10:24 AM" */
export function formatTime(iso: string): string {
  return upperMeridiem(TIME.format(new Date(iso)));
}

/** "just now", "5 minutes ago", "2h ago", "3 days ago". */
export function formatRelative(iso: string, now: number = Date.now()): string {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60_000));
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

export const PARCEL_TYPE_LABELS: Record<ParcelType, string> = {
  DOCUMENT: 'Document',
  PARCEL: 'Package',
  FRAGILE: 'Fragile',
  ELECTRONICS: 'Electronics',
  PERISHABLE: 'Perishable',
};

/** "30 × 20 × 15 cm", or null when no dimension is known. */
export function formatDimensions(
  lengthCm?: number | null,
  widthCm?: number | null,
  heightCm?: number | null,
): string | null {
  if (lengthCm == null && widthCm == null && heightCm == null) return null;
  return `${lengthCm ?? '-'} × ${widthCm ?? '-'} × ${heightCm ?? '-'} cm`;
}

/**
 * Statuses counted as "active": booked, on the way, or a failed attempt waiting to be retried or
 * closed. Only DELIVERED and FAILED are finished.
 */
export const ACTIVE_STATUSES: ParcelStatusCode[] = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'FAILED_ATTEMPT',
];

export function countActive(counts: StatusCounts): number {
  return ACTIVE_STATUSES.reduce((sum, status) => sum + counts[status], 0);
}

export function countAll(counts: StatusCounts): number {
  return Object.values(counts).reduce((sum, count) => sum + count, 0);
}

/** Default line under each status in a history list when the change has no note. */
export const HISTORY_TEXT: Record<ParcelStatusCode, string> = {
  CREATED: 'Delivery request has been created.',
  PICKED_UP: 'Parcel picked up from the sender.',
  IN_TRANSIT: 'Your parcel is on the way.',
  OUT_FOR_DELIVERY: 'Your parcel is out for delivery.',
  DELIVERED: 'Your parcel has been delivered.',
  FAILED_ATTEMPT: 'The delivery attempt failed.',
  FAILED: 'The delivery could not be completed.',
};

/** Every event keeps its own text: each failed attempt and the close show their own reason. */
export function historyText(item: StatusHistoryItem): string {
  if (isFailureStatus(item.status) && item.failureReason) return `Reason: ${item.failureReason}`;
  return item.note || HISTORY_TEXT[item.status];
}

/**
 * The reason to show for a parcel that is currently a failed attempt or failed: the reason of the
 * latest failure event in the history (oldest first). {@code null} otherwise.
 */
export function latestFailureReason(history: StatusHistoryItem[]): string | null {
  const current = history.at(-1);
  if (!current || !isFailureStatus(current.status)) return null;
  for (let i = history.length - 1; i >= 0; i--) {
    const item = history[i];
    if (isFailureStatus(item.status) && item.failureReason) return item.failureReason;
  }
  return null;
}

export interface ProgressStep {
  code: ParcelStatusCode;
  label: string;
  state: StepState;
  reachedAt?: string;
}

const PROGRESS: ParcelStatusCode[] = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

/**
 * The five steps of the "Delivery Progress" bar. The latest reached step is "current" unless the
 * parcel is delivered. A parcel that is now a failed attempt (or failed) ends with that step in
 * place of the steps it never reached. Only the current attempt counts (history since the latest
 * CREATED), so a retried parcel's progress starts over; history lists still show every event.
 */
export function buildProgress(history: StatusHistoryItem[]): ProgressStep[] {
  const attempt = sinceLatestCreated(history, (item) => item.status);
  const reachedAt = new Map<ParcelStatusCode, string>();
  for (const item of attempt) reachedAt.set(item.status, item.changedAt);

  const current = attempt.at(-1)?.status;
  const failure = current && isFailureStatus(current) ? current : null;
  const failedAt = failure ? reachedAt.get(failure) : undefined;
  const lastReached = PROGRESS.reduce((last, code, i) => (reachedAt.has(code) ? i : last), -1);

  const steps: ProgressStep[] = PROGRESS.map((code, i) => {
    let state: StepState = 'upcoming';
    if (i < lastReached || (i === lastReached && (code === 'DELIVERED' || failure))) {
      state = 'complete';
    } else if (i === lastReached) {
      state = 'current';
    }
    return { code, label: STATUS_META[code].label, state, reachedAt: reachedAt.get(code) };
  });

  if (!failure) return steps;
  return [
    ...steps.slice(0, lastReached + 1),
    { code: failure, label: STATUS_META[failure].label, state: 'current', reachedAt: failedAt },
  ];
}
