import { useState } from 'react';
import { dispatchApi } from '../../../api/dispatch';
import { toApiRequestError } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import TextAreaField from '../../../components/ui/TextAreaField';
import { MAX_REASON_LENGTH } from '../../../types/dispatch';
import Modal from '../../shared-dispatch/components/Modal';
import type { FailedParcelRef } from '../failedActions';

interface RetryDialogProps {
  parcel: FailedParcelRef;
  onClose: () => void;
  onRetried: (parcel: FailedParcelRef) => void;
}

/** Retries a failed attempt: the parcel goes back to Unassigned. The note is optional. */
export default function RetryDialog({ parcel, onClose, onRetried }: RetryDialogProps) {
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [saving, setSaving] = useState(false);

  async function submit() {
    if (saving) return;
    setSaving(true);
    setMessage(undefined);
    try {
      await dispatchApi.retry(parcel.parcelId, note.trim() || undefined);
      onRetried(parcel);
    } catch (err) {
      // e.g. 409: someone else already retried or closed it.
      const error = toApiRequestError(err);
      const onNote = error.fieldErrors.find((f) => f.field === 'note');
      if (onNote) setNoteError(onNote.message);
      else setMessage(error.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      title={`Retry #${parcel.trackingNumber}?`}
      description="The parcel goes back to Unassigned Parcels. Assign a courier there afterwards."
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving} disabled={saving}>
            Retry Delivery
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {message && <Alert tone="error">{message}</Alert>}
        <TextAreaField
          label="Note"
          optional
          rows={2}
          value={note}
          maxLength={MAX_REASON_LENGTH}
          onChange={(e) => {
            setNote(e.target.value);
            setNoteError(undefined);
          }}
          error={noteError}
          hint={`${note.length} / ${MAX_REASON_LENGTH}`}
          disabled={saving}
        />
      </div>
    </Modal>
  );
}
