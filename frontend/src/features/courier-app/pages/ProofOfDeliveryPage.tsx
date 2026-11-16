import { ArrowLeft, Camera, PackageSearch, RotateCcw, Trash2 } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { generatePath, Link, useNavigate, useParams } from 'react-router';
import { ApiRequestError, getErrorMessage } from '../../../api/errors';
import LoadingState from '../../../components/feedback/LoadingState';
import { EmptyState, ErrorState } from '../../../components/feedback/States';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import TextAreaField from '../../../components/ui/TextAreaField';
import TextField from '../../../components/ui/TextField';
import { PATHS } from '../../../routes/paths';
import SectionCard from '../../shared-parcels/components/SectionCard';
import { statusVisual } from '../../shared-parcels/components/statusVisuals';
import { useApiData } from '../../shared-parcels/useApiData';
import { courierApi } from '../api';
import StopDetails from '../components/StopDetails';
import { resizePhoto } from '../photo';
import {
  MAX_DELIVERY_NOTE,
  MAX_RECIPIENT_NAME,
  type CourierDeliveryDetail,
  type CourierNotice,
} from '../types';

interface Photo {
  blob: Blob;
  previewUrl: string;
}

/** The camera photo, resized in the browser, with a preview. */
function usePhoto() {
  const [photo, setPhoto] = useState<Photo | null>(null);
  const [preparing, setPreparing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Free the preview's memory when it is replaced or the page closes.
  useEffect(
    () => () => {
      if (photo) URL.revokeObjectURL(photo.previewUrl);
    },
    [photo],
  );

  async function choose(file: File) {
    setError(null);
    setPreparing(true);
    try {
      const blob = await resizePhoto(file);
      setPhoto({ blob, previewUrl: URL.createObjectURL(blob) });
    } catch {
      setError("Couldn't use this photo. Take it again, or deliver without a photo.");
    } finally {
      setPreparing(false);
    }
  }

  return { photo, preparing, error, choose, clear: () => setPhoto(null) };
}

/** The message for a failed upload; the server's own message otherwise. */
function deliverError(err: unknown): string {
  if (err instanceof ApiRequestError && err.status === 413) {
    return 'The photo is too large. Retake it, or deliver without a photo.';
  }
  if (err instanceof ApiRequestError && err.fieldErrors.length > 0) {
    return err.fieldErrors.map((f) => f.message).join(' ');
  }
  return getErrorMessage(err);
}

interface ProofFormProps {
  delivery: CourierDeliveryDetail;
  /** Called after a 409 or 404: the page reloads the delivery to show where it stands. */
  onStale: () => void;
}

function ProofForm({ delivery, onStale }: ProofFormProps) {
  const navigate = useNavigate();
  const inputId = useId();
  const fileInput = useRef<HTMLInputElement>(null);
  const [recipientName, setRecipientName] = useState(delivery.dropoff.contactName);
  const [note, setNote] = useState('');
  const [showErrors, setShowErrors] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { photo, preparing, error: photoError, choose, clear } = usePhoto();

  const nameError =
    recipientName.length > MAX_RECIPIENT_NAME
      ? `Keep the name to ${MAX_RECIPIENT_NAME} characters or fewer.`
      : showErrors && recipientName.trim() === ''
        ? 'Enter who received the parcel.'
        : undefined;
  const noteTooLong = note.length > MAX_DELIVERY_NOTE;

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Reset so taking "the same" photo again still fires a change.
    event.target.value = '';
    if (file) void choose(file);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setShowErrors(true);
    if (saving || preparing || recipientName.trim() === '' || nameError || noteTooLong) return;
    setSaving(true);
    setError(null);
    try {
      await courierApi.deliver(delivery.parcelId, {
        recipientName,
        note,
        photo: photo?.blob,
      });
      const state: CourierNotice = { notice: `${delivery.trackingNumber} was delivered.` };
      navigate(PATHS.courierHome, { state });
    } catch (err) {
      setSaving(false);
      if (err instanceof ApiRequestError && (err.status === 409 || err.status === 404)) onStale();
      else setError(deliverError(err));
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      <SectionCard>
        <StopDetails label="Deliver to" stop={delivery.dropoff} />
      </SectionCard>

      <TextField
        label="Received by"
        value={recipientName}
        onChange={(e) => setRecipientName(e.target.value)}
        autoComplete="off"
        hint="The person who took the parcel, if not the recipient."
        error={nameError}
      />

      <SectionCard title="Photo" subtitle="A photo of the parcel at the door. Optional.">
        <input
          ref={fileInput}
          id={inputId}
          type="file"
          accept="image/*"
          capture="environment"
          className="sr-only"
          onChange={onFile}
          aria-label="Photo of the delivered parcel"
        />
        {photo ? (
          <div className="flex flex-col gap-3">
            <img
              src={photo.previewUrl}
              alt="Proof of delivery"
              className="max-h-72 w-full rounded-xl object-contain"
            />
            <div className="grid grid-cols-2 gap-2">
              <Button variant="outline" onClick={() => fileInput.current?.click()}>
                <RotateCcw className="size-4" aria-hidden="true" />
                Retake
              </Button>
              <Button variant="ghost" onClick={clear}>
                <Trash2 className="size-4" aria-hidden="true" />
                Remove
              </Button>
            </div>
          </div>
        ) : (
          <Button
            variant="outline"
            size="lg"
            fullWidth
            loading={preparing}
            onClick={() => fileInput.current?.click()}
          >
            <Camera className="size-4" aria-hidden="true" />
            {preparing ? 'Preparing photo' : 'Take Photo'}
          </Button>
        )}
        {photoError && (
          <p className="mt-2 text-sm text-red-600" role="alert">
            {photoError}
          </p>
        )}
      </SectionCard>

      <TextAreaField
        label="Note"
        optional
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="For example: left with the security guard"
        hint={`${note.length} / ${MAX_DELIVERY_NOTE}`}
        error={
          noteTooLong ? `Keep the note to ${MAX_DELIVERY_NOTE} characters or fewer.` : undefined
        }
      />

      {error && (
        <Alert tone="error" title="Couldn't confirm the delivery">
          {error}
        </Alert>
      )}

      <Button type="submit" size="lg" fullWidth loading={saving} disabled={preparing}>
        Confirm Delivery
      </Button>
    </form>
  );
}

/** Why the form isn't offered: not out for delivery yet, or already finished. */
function CannotDeliver({
  delivery,
  conflict,
  detailsPath,
}: {
  delivery: CourierDeliveryDetail;
  conflict: boolean;
  detailsPath: string;
}) {
  return (
    <div className="flex flex-col gap-4">
      <Alert
        tone={conflict ? 'warning' : 'info'}
        title={conflict ? 'This delivery was just updated' : "This delivery can't be confirmed yet"}
      >
        {delivery.allowedActions.length > 0
          ? `It is ${statusVisual(delivery.currentStatus).label}. Mark it out for delivery first.`
          : `This delivery is now ${statusVisual(delivery.currentStatus).label}.`}
      </Alert>
      <ButtonLink to={detailsPath} variant="outline" fullWidth>
        Open the delivery
      </ButtonLink>
    </div>
  );
}

/** Confirms a delivery with the recipient's name and, optionally, a photo and a note. */
export default function ProofOfDeliveryPage() {
  const { parcelId = '' } = useParams();
  const [conflict, setConflict] = useState(false);
  const result = useApiData(`courier-proof:${parcelId}`, () => courierApi.getDelivery(parcelId));
  const detailsPath = generatePath(PATHS.courierDeliveryDetail, { parcelId });

  const delivery = result.status === 'success' ? result.data : null;
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

      <h1 className="text-xl font-bold text-slate-900">Proof of Delivery</h1>
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

        {delivery && delivery.allowedActions.includes('DELIVERED') && (
          <ProofForm
            delivery={delivery}
            onStale={() => {
              setConflict(true);
              result.retry();
            }}
          />
        )}

        {delivery && !delivery.allowedActions.includes('DELIVERED') && (
          <CannotDeliver delivery={delivery} conflict={conflict} detailsPath={detailsPath} />
        )}
      </div>
    </section>
  );
}
