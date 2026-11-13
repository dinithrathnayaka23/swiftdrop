import ButtonLink from '../../../components/ui/ButtonLink';
import type { ParcelStatusCode } from '../../../types/tracking';
import { courierActions } from '../actions';

interface DeliveryActionsProps {
  parcelId: string;
  allowedActions: ParcelStatusCode[];
}

/** One full-width button per next step the backend allows; nothing once delivered or failed. */
export default function DeliveryActions({ parcelId, allowedActions }: DeliveryActionsProps) {
  const actions = courierActions(parcelId, allowedActions);
  if (actions.length === 0) return null;
  return (
    <div className="flex flex-col gap-3">
      {actions.map((action) => (
        <ButtonLink
          key={action.status}
          to={action.to}
          variant={action.variant}
          size="lg"
          fullWidth
          className={action.status === 'FAILED_ATTEMPT' ? 'border-red-300 text-red-700' : undefined}
        >
          {action.label}
        </ButtonLink>
      ))}
    </div>
  );
}
