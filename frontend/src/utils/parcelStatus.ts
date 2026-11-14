import type { ParcelStatusCode, TrackingResult } from '../types/tracking';

export type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

interface StatusMeta {
  label: string;
  description: string;
  tone: StatusTone;
}

export const STATUS_META: Record<ParcelStatusCode, StatusMeta> = {
  CREATED: {
    label: 'Created',
    description: 'Your delivery is booked and waiting for pickup.',
    tone: 'neutral',
  },
  PICKED_UP: {
    label: 'Picked Up',
    description: 'Your parcel has been collected from the sender.',
    tone: 'info',
  },
  IN_TRANSIT: {
    label: 'In Transit',
    description: 'Your parcel is on the way to the next facility.',
    tone: 'info',
  },
  OUT_FOR_DELIVERY: {
    label: 'Out for Delivery',
    description: 'Your parcel is out for delivery and will arrive soon.',
    tone: 'warning',
  },
  DELIVERED: {
    label: 'Delivered',
    description: 'Your parcel has been delivered successfully.',
    tone: 'success',
  },
  FAILED_ATTEMPT: {
    label: 'Delivery attempt failed',
    description: 'We could not deliver your parcel this time. We will try again or contact you.',
    tone: 'danger',
  },
  FAILED: {
    label: 'Failed',
    description: 'We could not deliver your parcel. The reason has been recorded.',
    tone: 'danger',
  },
};

/** A failed attempt (not final: retried or closed by the dispatcher) or a failed delivery (final). */
export function isFailureStatus(status: ParcelStatusCode): boolean {
  return status === 'FAILED_ATTEMPT' || status === 'FAILED';
}

/** Only DELIVERED and FAILED are end states; a FAILED_ATTEMPT parcel can still be retried. */
export function isFinalStatus(status: ParcelStatusCode): boolean {
  return status === 'DELIVERED' || status === 'FAILED';
}

/**
 * The part of a history (oldest first) that belongs to the current delivery attempt: from the
 * latest CREATED on. A retried parcel goes back to CREATED, so its step indicator starts again;
 * full history lists keep every event.
 */
export function sinceLatestCreated<T>(history: T[], statusOf: (item: T) => ParcelStatusCode): T[] {
  for (let i = history.length - 1; i >= 0; i--) {
    if (statusOf(history[i]) === 'CREATED') return history.slice(i);
  }
  return history;
}

/** The four milestones shown on the tracking timeline. */
export const TIMELINE_STEPS: ParcelStatusCode[] = [
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

export type StepState = 'complete' | 'current' | 'upcoming';

export interface TimelineStep {
  code: ParcelStatusCode;
  label: string;
  state: StepState;
  reachedAt?: string;
}

/**
 * Marks milestones reached so far in the current attempt (since the latest CREATED); the latest
 * reached one is "current" unless delivered.
 */
export function buildTimeline(result: TrackingResult): TimelineStep[] {
  const reachedAt = new Map<ParcelStatusCode, string>();
  for (const event of sinceLatestCreated(result.history, (e) => e.statusCode)) {
    reachedAt.set(event.statusCode, event.changedAt);
  }

  let lastReached = -1;
  TIMELINE_STEPS.forEach((code, i) => {
    if (reachedAt.has(code)) lastReached = i;
  });

  return TIMELINE_STEPS.map((code, i) => {
    let state: StepState = 'upcoming';
    if (i < lastReached) state = 'complete';
    else if (i === lastReached) state = code === 'DELIVERED' ? 'complete' : 'current';
    return { code, label: STATUS_META[code].label, state, reachedAt: reachedAt.get(code) };
  });
}
