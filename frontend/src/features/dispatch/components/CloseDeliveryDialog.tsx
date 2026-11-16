import { useState } from 'react';
import { dispatchApi } from '../../../api/dispatch';
import { toApiRequestError } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import TextAreaField from '../../../components/ui/TextAreaField';
import { MAX_REASON_LENGTH } from '../../../types/dispatch';
import type { FailedDeliveryAction } from '../../../types/settings';
import Modal from '../../shared-dispatch/components/Modal';
import { closeReasonFor, type FailedParcelRef } from '../failedActions';

interface CloseDeliveryDialogProps {
  parcel: FailedParcelRef;
  /** The System Settings default; pre-fills the reason. */
  defaultAction?: FailedDeliveryAction;
  onClose: () => void;
  onClosed: (parcel: FailedParcelRef) => void;
}

/** Closes a failed attempt for good (FAILED). The reason is required (CloseDeliveryRequest). */
export default function CloseDeliveryDialog({
  parcel,
  defaultAction,
  onClose,
  onClosed,
}: CloseDeliveryDialogProps) {
  const [reason, setReason] = useState(() => closeReasonFor(defaultAction));
  const [reasonError, setReasonError] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [saving, setSaving] = useState(false);

  async function submit() {
    if (saving) return;
    if (!reason.trim()) {
      setReasonError('Reason is required');
      return;
    }
    setSaving(true);
    setMessage(undefined);
    try {
      await dispatchApi.close(parcel.parcelId, reason.trim());
      onClosed(parcel);
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
      title={`Close #${parcel.trackingNumber}?`}
      description="The delivery ends as failed. This can't be undone."
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button variant="danger" onClick={submit} loading={saving} disabled={saving}>
            Close Delivery
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
      </div>
    </Modal>
  );
}
