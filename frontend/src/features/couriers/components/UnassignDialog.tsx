import { useState } from 'react';
import { dispatchApi } from '../../../api/dispatch';
import { toApiRequestError } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import TextAreaField from '../../../components/ui/TextAreaField';
import { MAX_REASON_LENGTH } from '../../../types/dispatch';
import Modal from '../../shared-dispatch/components/Modal';

interface UnassignDialogProps {
  parcelId: string;
  trackingNumber: string;
  onClose: () => void;
  onUnassigned: () => void;
}

/** Takes a parcel back from its courier before pickup; the reason is optional. */
export default function UnassignDialog({
  parcelId,
  trackingNumber,
  onClose,
  onUnassigned,
}: UnassignDialogProps) {
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [saving, setSaving] = useState(false);

  async function submit() {
    if (saving) return;
    setSaving(true);
    setMessage(undefined);
    try {
      await dispatchApi.unassign(parcelId, reason.trim() || undefined);
      onUnassigned();
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
      title={`Unassign #${trackingNumber}?`}
      description="The parcel goes back to Unassigned Parcels and waits for a courier again."
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button variant="danger" onClick={submit} loading={saving} disabled={saving}>
            Unassign
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {message && <Alert tone="error">{message}</Alert>}
        <TextAreaField
          label="Reason"
          optional
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
      </div>
    </Modal>
  );
}
