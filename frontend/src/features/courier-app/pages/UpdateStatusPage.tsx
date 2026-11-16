import { ArrowLeft, ArrowRight, PackageSearch } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { generatePath, Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiRequestError, getErrorMessage } from '../../../api/errors';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import TextAreaField from '../../../components/ui/TextAreaField';
import { PATHS } from '../../../routes/paths';
import type { ParcelStatusCode } from '../../../types/tracking';
import SectionCard from '../../shared-parcels/components/SectionCard';
import StatusPill from '../../shared-parcels/components/StatusPill';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { useApiData } from '../../shared-parcels/useApiData';
import { courierApi } from '../api';
import StopDetails from '../components/StopDetails';
import {
  COURIER_STEP_STATUSES,
  MAX_STATUS_NOTE,
  type CourierDeliveryDetail,
  type CourierStepStatus,
} from '../types';

const CONFIRM_LABELS: Record<CourierStepStatus, string> = {
  PICKED_UP: 'Confirm Pickup',
  IN_TRANSIT: 'Confirm In Transit',
  OUT_FOR_DELIVERY: 'Confirm Out for Delivery',
};

const HINTS: Record<CourierStepStatus, string> = {
  PICKED_UP: 'Confirm once you have the parcel with you.',
  IN_TRANSIT: 'Confirm when the parcel is on its way to the recipient’s area.',
  OUT_FOR_DELIVERY: 'Confirm when you are setting off to the recipient.',
};

function isStepStatus(value: string | null): value is CourierStepStatus {
  return COURIER_STEP_STATUSES.includes(value as CourierStepStatus);
}

/** The step from the link, or the parcel's next plain step when the link has none or a bad one. */
function stepFor(requested: string | null, allowed: ParcelStatusCode[]): CourierStepStatus | null {
  if (requested !== null) return isStepStatus(requested) ? requested : null;
  return allowed.find(isStepStatus) ?? null;
}

interface StepFormProps {
  delivery: CourierDeliveryDetail;
  step: CourierStepStatus;
  /** Called after a 409: the page reloads the delivery to show its real status. */
  onConflict: () => void;
  /** Called after a 404: the delivery is no longer the courier's. */
  onGone: () => void;
}

function StepForm({ delivery, step, onConflict, onGone }: StepFormProps) {
  const navigate = useNavigate();
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const tooLong = note.length > MAX_STATUS_NOTE;
  const nextStop = step === 'PICKED_UP' ? delivery.pickup : delivery.dropoff;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (saving || tooLong) return;
    setSaving(true);
    setError(null);
    try {
      await courierApi.updateStatus(delivery.parcelId, { status: step, note });
      navigate(generatePath(PATHS.courierDeliveryDetail, { parcelId: delivery.parcelId }));
    } catch (err) {
      setSaving(false);
      if (err instanceof ApiRequestError && err.status === 409) onConflict();
      else if (err instanceof ApiRequestError && err.status === 404) onGone();
      else setError(getErrorMessage(err));
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      <SectionCard>
        <div className="flex flex-wrap items-center gap-3">
          <StatusPill visual={statusVisual(delivery.currentStatus)} />
          <ArrowRight className="size-4 text-slate-400" aria-hidden="true" />
          <span className="sr-only">to</span>
          <StatusPill visual={statusVisual(step)} />
        </div>
        <p className="mt-3 text-sm text-slate-600">{HINTS[step]}</p>
      </SectionCard>

      <SectionCard>
        <StopDetails label={step === 'PICKED_UP' ? 'Pick up from' : 'Deliver to'} stop={nextStop} />
      </SectionCard>

      <TextAreaField
        label="Note"
        optional
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="For example: collected from the front desk"
        hint={`${note.length} / ${MAX_STATUS_NOTE}`}
        error={tooLong ? `Keep the note to ${MAX_STATUS_NOTE} characters or fewer.` : undefined}
      />

      {error && (
        <Alert tone="error" title="Couldn't update the status">
          {error}
        </Alert>
      )}

      <Button type="submit" size="lg" fullWidth loading={saving} disabled={tooLong}>
        {CONFIRM_LABELS[step]}
      </Button>
    </form>
  );
}

/** Records a plain step (picked up, in transit, out for delivery) for one of the courier's deliveries. */
export default function UpdateStatusPage() {
  const { parcelId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const requested = searchParams.get('status');
  const [conflict, setConflict] = useState(false);
  const result = useApiData(`courier-delivery:${parcelId}`, () => courierApi.getDelivery(parcelId));
  const detailsPath = generatePath(PATHS.courierDeliveryDetail, { parcelId });

  const delivery = result.status === 'success' ? result.data : null;
  const step = delivery ? stepFor(requested, delivery.allowedActions) : null;
  const canTakeStep = delivery !== null && step !== null && delivery.allowedActions.includes(step);

  return (
    <section>
      <Link
        to={result.status === 'success' && result.data === null ? PATHS.courierHome : detailsPath}
        className="mb-4 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Back to the delivery
      </Link>

      <h1 className="text-xl font-bold text-slate-900">Update Status</h1>
      {delivery && (
        <p className="mt-0.5 font-mono text-sm text-slate-500">{delivery.trackingNumber}</p>
      )}

      <div className="mt-4">
        {result.status === 'loading' && <LoadingState message="Loading the delivery." />}

        {result.status === 'error' && (
          <ErrorState
            title="Couldn't load this delivery"
            message={result.message}
            onAction={result.retry}
          />
        )}

        {result.status === 'success' && result.data === null && (
          <EmptyState
            icon={PackageSearch}
            title="Delivery not found"
            message="This delivery isn't assigned to you, or it no longer exists."
            action={
              <ButtonLink to={PATHS.courierHome} variant="outline">
                Back to today's deliveries
              </ButtonLink>
            }
          />
        )}

        {delivery && canTakeStep && (
          <StepForm
            delivery={delivery}
            step={step}
            onConflict={() => {
              setConflict(true);
              result.retry();
            }}
            onGone={result.retry}
          />
        )}

        {delivery && !canTakeStep && (
          <div className="flex flex-col gap-4">
            <Alert
              tone={conflict ? 'warning' : 'info'}
              title={conflict ? 'This delivery was just updated' : 'Nothing to update here'}
            >
              This delivery is now <strong>{statusVisual(delivery.currentStatus).label}</strong>.
              {delivery.allowedActions.length > 0
                ? ' Open the delivery to take the next step.'
                : ' It needs no more updates.'}
            </Alert>
            <ButtonLink to={detailsPath} variant="outline" fullWidth>
              Open the delivery
            </ButtonLink>
          </div>
        )}
      </div>
    </section>
  );
}
