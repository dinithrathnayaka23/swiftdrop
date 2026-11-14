import { useState } from 'react';
import { dispatchApi } from '../../../api/dispatch';
import { toApiRequestError } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import TextAreaField from '../../../components/ui/TextAreaField';
import type { Courier } from '../../../types/courier';
import { MAX_REASON_LENGTH } from '../../../types/dispatch';
import Modal from '../../shared-dispatch/components/Modal';
import OnlineCourierPicker from '../../shared-dispatch/components/OnlineCourierPicker';

interface ReassignDialogProps {
  parcelId: string;
  trackingNumber: string;
  /** The parcel's courier now; left out of the list. */
  currentCourierId: string;
  onClose: () => void;
  onReassigned: (courier: Courier) => void;
}

/** Moves a parcel to another online courier; the reason is required (ReassignParcelRequest). */
export default function ReassignDialog({
  parcelId,
  trackingNumber,
  currentCourierId,
  onClose,
  onReassigned,
}: ReassignDialogProps) {
  const [courier, setCourier] = useState<Courier | null>(null);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string>();
  const [courierError, setCourierError] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [saving, setSaving] = useState(false);

  async function submit() {
    if (saving) return;
    const trimmed = reason.trim();
    const noReason = !trimmed
      ? 'Reason is required'
      : reason.length > MAX_REASON_LENGTH
        ? `Reason must be at most ${MAX_REASON_LENGTH} characters`
        : undefined;
    setReasonError(noReason);
    setCourierError(courier ? undefined : 'Choose a courier');
    if (noReason || !courier) return;
    setSaving(true);
    setMessage(undefined);
    try {
      await dispatchApi.reassign(parcelId, courier.id, trimmed);
      onReassigned(courier);
    } catch (err) {
      const error = toApiRequestError(err);
      const onReason = error.fieldErrors.find((f) => f.field === 'reason');
      if (onReason) setReasonError(onReason.message);
      else setMessage(error.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      title={`Reassign #${trackingNumber}`}
      description="Give this parcel to another online courier."
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving} disabled={saving}>
            Reassign
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {message && <Alert tone="error">{message}</Alert>}
        <TextAreaField
          label="Reason"
          rows={2}
          value={reason}
          maxLength={MAX_REASON_LENGTH}
          onChange={(e) => {
            setReason(e.target.value);
            setReasonError(undefined);
          }}
          error={reasonError}
          hint={`${reason.length} / ${MAX_REASON_LENGTH}`}
          disabled={saving}
        />
        <OnlineCourierPicker
          selectedId={courier?.id ?? null}
          excludeId={currentCourierId}
          onSelect={(picked) => {
            setCourier(picked);
            setCourierError(undefined);
            setMessage(undefined);
          }}
          disabled={saving}
        />
        {courierError && (
          <p role="alert" className="text-sm text-red-600">
            {courierError}
          </p>
        )}
      </div>
    </Modal>
  );
}
