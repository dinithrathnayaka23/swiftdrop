import type { FailedDeliveryAction } from '../../types/settings';

/**
 * The close reason pre-filled from the System Settings default (GET /failed-deliveries/default-action).
 * RETURN_TO_SENDER and HOLD_FOR_COLLECTION have no parcel status of their own: closing with this
 * reason records them. RETRY_DELIVERY pre-fills nothing (Retry is the emphasised action instead).
 */
export function closeReasonFor(action: FailedDeliveryAction | undefined): string {
  switch (action) {
    case 'RETURN_TO_SENDER':
      return 'Returned to sender';
    case 'HOLD_FOR_COLLECTION':
      return 'Held for collection';
    default:
      return '';
  }
}

/** The parcel a retry or close dialog acts on. */
export interface FailedParcelRef {
  parcelId: string;
  trackingNumber: string;
}
