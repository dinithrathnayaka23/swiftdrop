import { ArrowLeft, CircleX, PackageSearch } from 'lucide-react';
import { useCallback, useState, type FormEvent } from 'react';
import { generatePath, Link, useNavigate, useParams } from 'react-router';
import { ApiRequestError, getErrorMessage } from '../../../api/errors';
import ConfirmDialog from '../../../components/feedback/ConfirmDialog';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import TextAreaField from '../../../components/ui/TextAreaField';
import { PATHS } from '../../../routes/paths';
import { cn } from '../../../utils/cn';
import SectionCard from '../../shared-parcels/components/SectionCard';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { useApiData } from '../../shared-parcels/useApiData';
import { courierApi } from '../api';
import StopDetails from '../components/StopDetails';
import {
  MAX_FAILURE_NOTE,
  type CourierDeliveryDetail,
  type CourierNotice,
  type FailureReason,
} from '../types';

interface FailureFormProps {
  delivery: CourierDeliveryDetail;
  reasons: FailureReason[];
  /** Called after a 409 or 404: the page reloads the delivery to show where it stands. */
  onStale: () => void;
}

function FailureForm({ delivery, reasons, onStale }: FailureFormProps) {
  const navigate = useNavigate();
  const [reasonCode, setReasonCode] = useState('');
  const [note, setNote] = useState('');
  const [showErrors, setShowErrors] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reason = reasons.find((r) => r.code === reasonCode);
  const reasonError = showErrors && !reason ? 'Choose a reason.' : undefined;
  const noteMissing = Boolean(reason?.noteRequired) && note.trim() === '';
  const tooLong = note.length > MAX_FAILURE_NOTE;
  const noteError = tooLong
    ? `Keep the note to ${MAX_FAILURE_NOTE} characters or fewer.`
    : showErrors && noteMissing
      ? 'Add a note to explain this reason.'
      : undefined;

  const cancel = useCallback(() => setConfirming(false), []);

  function review(event: FormEvent) {
    event.preventDefault();
    setShowErrors(true);
    if (!reason || noteMissing || tooLong) return;
    setError(null);
    setConfirming(true);
  }

  async function report() {
    if (!reason || saving) return;
    setSaving(true);
    try {
      await courierApi.reportFailure(delivery.parcelId, { reasonCode: reason.code, note });
      const state: CourierNotice = {
        notice: `${delivery.trackingNumber} was reported as failed (${reason.label}).`,
      };
      navigate(PATHS.courierHome, { state });
    } catch (err) {
      setSaving(false);
      setConfirming(false);
      if (err instanceof ApiRequestError && (err.status === 409 || err.status === 404)) onStale();
      else setError(getErrorMessage(err));
    }
  }

  return (
    <form onSubmit={review} className="flex flex-col gap-4" noValidate>
      <SectionCard>
        <StopDetails label="Recipient" stop={delivery.dropoff} />
        <p className="mt-3 text-sm text-slate-500">Try calling the recipient before you report.</p>
      </SectionCard>

      <fieldset aria-describedby={reasonError ? 'failure-reason-error' : undefined}>
        <legend className="mb-2 text-sm font-semibold text-slate-900">
          Why couldn't you deliver?
        </legend>
        <div className="flex flex-col gap-2">
          {reasons.map((r) => (
            <label
              key={r.code}
              className={cn(
                'flex min-h-12 cursor-pointer items-center gap-3 rounded-xl border bg-white px-4 py-3 text-sm font-medium text-slate-800',
                r.code === reasonCode
                  ? 'border-brand-500 ring-1 ring-brand-500'
                  : 'border-slate-200',
              )}
            >
              <input
                type="radio"
                name="reason"
                value={r.code}
                checked={r.code === reasonCode}
                onChange={() => setReasonCode(r.code)}
                className="size-4 accent-brand-600"
              />
              <span className="flex-1">{r.label}</span>
              {r.noteRequired && <span className="text-xs text-slate-500">Note needed</span>}
            </label>
          ))}
        </div>
        {reasonError && (
          <p id="failure-reason-error" className="mt-2 text-sm text-red-600">
            {reasonError}
          </p>
        )}
      </fieldset>

      <TextAreaField
        label="Note"
        optional={!reason?.noteRequired}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="For example: gate locked, no answer on the phone"
        hint={`${note.length} / ${MAX_FAILURE_NOTE}`}
        error={noteError}
      />

      {error && (
        <Alert tone="error" title="Couldn't report the failed delivery">
          {error}
        </Alert>
      )}

      <Button type="submit" variant="danger" size="lg" fullWidth disabled={tooLong}>
        Report Failed Delivery
      </Button>

      <ConfirmDialog
        open={confirming}
        title="Mark this delivery as failed?"
        message={`${delivery.trackingNumber}: ${reason?.label ?? ''}. This can't be undone.`}
        confirmLabel="Mark as Failed"
        cancelLabel="Go back"
        icon={CircleX}
        loading={saving}
        onConfirm={report}
        onCancel={cancel}
      />
    </form>
  );
}

/** Why the form isn't offered: not picked up yet, or already finished. */
function CannotFail({
  delivery,
  conflict,
  detailsPath,
}: {
  delivery: CourierDeliveryDetail;
  conflict: boolean;
  detailsPath: string;
}) {
  const waiting = delivery.currentStatus === 'CREATED';
  return (
    <div className="flex flex-col gap-4">
      <Alert
        tone={conflict ? 'warning' : 'info'}
        title={
          conflict ? 'This delivery was just updated' : "This delivery can't be reported as failed"
        }
      >
        {waiting
          ? 'Pick up the parcel first. A delivery can only be reported as failed after pickup.'
          : `This delivery is now ${statusVisual(delivery.currentStatus).label}.`}
      </Alert>
      <ButtonLink to={detailsPath} variant="outline" fullWidth>
        Open the delivery
      </ButtonLink>
    </div>
  );
}

/** Reports one of the courier's deliveries as failed, with a reason and (for some reasons) a note. */
export default function FailedDeliveryPage() {
  const { parcelId = '' } = useParams();
  const [conflict, setConflict] = useState(false);
  const result = useApiData(`courier-failure:${parcelId}`, () =>
    Promise.all([courierApi.getDelivery(parcelId), courierApi.getFailureReasons()]),
  );
  const detailsPath = generatePath(PATHS.courierDeliveryDetail, { parcelId });

  const delivery = result.status === 'success' ? result.data[0] : null;
  const reasons = result.status === 'success' ? result.data[1] : [];
  const notFound = result.status === 'success' && delivery === null;

  return (
    <section>
      <Link
        to={notFound ? PATHS.courierHome : detailsPath}
        className="mb-4 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {notFound ? "Today's deliveries" : 'Back to the delivery'}
      </Link>

      <h1 className="text-xl font-bold text-slate-900">Report Failed Delivery</h1>
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

        {notFound && (
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

        {delivery && delivery.allowedActions.includes('FAILED') && (
          <FailureForm
            delivery={delivery}
            reasons={reasons}
            onStale={() => {
              setConflict(true);
              result.retry();
            }}
          />
        )}

        {delivery && !delivery.allowedActions.includes('FAILED') && (
          <CannotFail delivery={delivery} conflict={conflict} detailsPath={detailsPath} />
        )}
      </div>
    </section>
  );
}
