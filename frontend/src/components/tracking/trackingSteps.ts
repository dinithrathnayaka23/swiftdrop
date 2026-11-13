import type { ParcelStatusCode, TrackingResult } from '../../types/tracking';
import {
  isFailureStatus,
  sinceLatestCreated,
  STATUS_META,
  TIMELINE_STEPS,
} from '../../utils/parcelStatus';

/**
 * Timeline and wording for the public tracking result. Separate from utils/parcelStatus (shared
 * with the customer and admin parcel screens) because only public tracking shows a failed step.
 */

export type TrackingStepState = 'complete' | 'current' | 'failed' | 'upcoming';

export interface TrackingStep {
  code: ParcelStatusCode;
  label: string;
  state: TrackingStepState;
  /** When the step was reached; for the failed step, when the delivery failed. */
  reachedAt?: string;
}

/**
 * Picked Up → In Transit → Out for Delivery → Delivered, from the history (oldest first).
 *
 * - CREATED: every step is upcoming; nothing has happened yet.
 * - In progress: reached steps are complete, the latest one is current.
 * - DELIVERED: every step is complete.
 * - FAILED_ATTEMPT or FAILED: the timeline stops at the last step reached, which is marked failed;
 *   later steps stay upcoming.
 *
 * Only the current attempt counts (history since the latest CREATED): a retried parcel starts over.
 */
export function buildTrackingTimeline(result: TrackingResult): TrackingStep[] {
  const reachedAt = new Map<ParcelStatusCode, string>();
  for (const event of sinceLatestCreated(result.history, (e) => e.statusCode)) {
    reachedAt.set(event.statusCode, event.changedAt);
  }

  let lastReached = -1;
  TIMELINE_STEPS.forEach((code, i) => {
    if (reachedAt.has(code)) lastReached = i;
  });
  const failed = isFailureStatus(result.currentStatus);
  // A parcel can only fail once picked up, but don't hide the failure if the history is short.
  if (failed && lastReached < 0) lastReached = 0;

  return TIMELINE_STEPS.map((code, i) => {
    const step: TrackingStep = { code, label: STATUS_META[code].label, state: 'upcoming' };
    if (i < lastReached) {
      step.state = 'complete';
    } else if (i === lastReached) {
      if (failed) step.state = 'failed';
      else step.state = code === 'DELIVERED' ? 'complete' : 'current';
    }
    if (step.state !== 'upcoming') {
      step.reachedAt =
        step.state === 'failed'
          ? (reachedAt.get(result.currentStatus) ?? result.lastUpdatedAt)
          : reachedAt.get(code);
    }
    return step;
  });
}

interface StatusText {
  title: string;
  description: string;
}

/**
 * The "Current Status" panel. Booked and failed parcels get wording of their own: the public API
 * never sends a failure reason, so the failed text stays general.
 */
export const TRACKING_STATUS_TEXT: Record<ParcelStatusCode, StatusText> = {
  CREATED: {
    title: 'Awaiting Pickup',
    description:
      'Your delivery is booked. A courier will collect the parcel from the sender soon, and this page will update once it is on its way.',
  },
  PICKED_UP: { title: STATUS_META.PICKED_UP.label, description: STATUS_META.PICKED_UP.description },
  IN_TRANSIT: {
    title: STATUS_META.IN_TRANSIT.label,
    description: STATUS_META.IN_TRANSIT.description,
  },
  OUT_FOR_DELIVERY: {
    title: STATUS_META.OUT_FOR_DELIVERY.label,
    description: STATUS_META.OUT_FOR_DELIVERY.description,
  },
  DELIVERED: { title: STATUS_META.DELIVERED.label, description: STATUS_META.DELIVERED.description },
  FAILED_ATTEMPT: {
    title: 'Delivery Attempt Failed',
    description:
      "We couldn't deliver the parcel this time. We will try again or contact you about the next step.",
  },
  FAILED: {
    title: 'Delivery Unsuccessful',
    description:
      "We weren't able to complete this delivery. Our team will arrange the next step with the sender.",
  },
};
