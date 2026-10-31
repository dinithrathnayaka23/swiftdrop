import type { ParcelStatusCode, TrackingResult } from '../types/tracking';

export type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

interface StatusMeta {
  label: string;
  description: string;
  tone: StatusTone;
}

export const STATUS_META: Record<ParcelStatusCode, StatusMeta> = {
  BOOKED: {
    label: 'Booked',
    description: 'Your delivery is booked and waiting for a courier.',
    tone: 'neutral',
  },
  ASSIGNED: {
    label: 'Courier Assigned',
    description: 'A courier has been assigned to collect your parcel.',
    tone: 'info',
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
    label: 'Delivery Attempt Failed',
    description: 'We could not deliver your parcel. We will try again soon.',
    tone: 'danger',
  },
  RETURNED: {
    label: 'Returned to Sender',
    description: 'Your parcel has been returned to the sender.',
    tone: 'neutral',
  },
  CANCELLED: {
    label: 'Cancelled',
    description: 'This delivery has been cancelled.',
    tone: 'danger',
  },
};

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

/** Marks milestones reached so far; the latest reached one is "current" unless delivered. */
export function buildTimeline(result: TrackingResult): TimelineStep[] {
  const reachedAt = new Map<ParcelStatusCode, string>();
  for (const event of result.history) reachedAt.set(event.statusCode, event.changedAt);

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
