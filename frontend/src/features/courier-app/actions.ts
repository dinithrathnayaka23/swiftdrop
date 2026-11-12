import { generatePath } from 'react-router';
import type { ButtonVariant } from '../../components/ui/buttonStyles';
import { PATHS } from '../../routes/paths';
import type { ParcelStatusCode } from '../../types/tracking';

export interface CourierAction {
  status: ParcelStatusCode;
  label: string;
  /** Screen that records the change. */
  to: string;
  variant: ButtonVariant;
}

const LABELS: Record<ParcelStatusCode, string> = {
  CREATED: 'Created',
  PICKED_UP: 'Mark as Picked Up',
  IN_TRANSIT: 'Mark In Transit',
  OUT_FOR_DELIVERY: 'Mark Out for Delivery',
  DELIVERED: 'Confirm Delivery',
  FAILED: 'Report Failed Delivery',
};

/**
 * The buttons for a delivery, from the API's {@code allowedActions}: DELIVERED opens Proof of
 * Delivery, FAILED opens the Failed Delivery form, the other steps open Update Status.
 */
export function courierActions(
  parcelId: string,
  allowedActions: ParcelStatusCode[],
): CourierAction[] {
  return allowedActions.map((status) => {
    if (status === 'DELIVERED') {
      return {
        status,
        label: LABELS[status],
        to: generatePath(PATHS.courierProof, { parcelId }),
        variant: 'primary',
      };
    }
    if (status === 'FAILED') {
      return {
        status,
        label: LABELS[status],
        to: generatePath(PATHS.courierFailed, { parcelId }),
        variant: 'outline',
      };
    }
    return {
      status,
      label: LABELS[status],
      to: `${generatePath(PATHS.courierUpdateStatus, { parcelId })}?status=${status}`,
      variant: 'primary',
    };
  });
}
