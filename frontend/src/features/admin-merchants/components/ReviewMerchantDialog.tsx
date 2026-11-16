import { CircleCheck, CircleX } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import TextAreaField from '../../../components/ui/TextAreaField';
import { MAX_REJECTION_REASON, type Merchant } from '../types';

export type ReviewDecision = 'approve' | 'reject';

interface ReviewMerchantDialogProps {
  merchant: Merchant | null;
  decision: ReviewDecision;
  saving: boolean;
  /** Server error from the last attempt, shown inside the dialog. */
  error: string | null;
  /** Called with the trimmed reason when rejecting, or undefined when approving. */
  onConfirm: (reason?: string) => void;
  onCancel: () => void;
}

/** Approve or reject a pending merchant. Rejecting needs a reason, which the merchant is emailed. */
export default function ReviewMerchantDialog({
  merchant,
  decision,
  saving,
  error,
  onConfirm,
  onCancel,
}: ReviewMerchantDialogProps) {
  const titleId = useId();
  const messageId = useId();
  const cancelRef = useRef<HTMLButtonElement>(null);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | undefined>();
  const open = merchant !== null;

  // Latest values for the Escape handler, so it isn't re-registered on every parent render.
  const escape = useRef({ saving, onCancel });
  useEffect(() => {
    escape.current = { saving, onCancel };
  });

  // Focus Cancel once when the dialog opens (not on later renders, which would pull focus out of
  // the reason box), and close on Escape.
  useEffect(() => {
    if (!open) return;
    cancelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !escape.current.saving) escape.current.onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  if (!merchant) return null;

  const approving = decision === 'approve';
  const Icon = approving ? CircleCheck : CircleX;

  function submit() {
    if (approving) {
      onConfirm();
      return;
    }
    const trimmed = reason.trim();
    if (!trimmed) {
      setReasonError('Please give a reason for the rejection');
      return;
    }
    if (trimmed.length > MAX_REJECTION_REASON) {
      setReasonError(`Reason must be at most ${MAX_REJECTION_REASON} characters`);
      return;
    }
    onConfirm(trimmed);
  }

  function cancel() {
    setReason('');
    setReasonError(undefined);
    onCancel();
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-[2px]"
      onClick={saving ? undefined : cancel}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={messageId}
        className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className={`flex size-12 items-center justify-center rounded-full ${
            approving ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'
          }`}
        >
          <Icon className="size-5" aria-hidden="true" />
        </div>
        <h2 id={titleId} className="mt-4 text-base font-semibold text-slate-900">
          {approving ? `Approve ${merchant.businessName}?` : `Reject ${merchant.businessName}?`}
        </h2>
        <p id={messageId} className="mt-2 text-sm text-slate-500">
          {approving
            ? 'The merchant account is activated and the contact person is emailed that they can sign in.'
            : 'The contact person is emailed this reason and their account is deactivated.'}
        </p>

        {!approving && (
          <div className="mt-4">
            <TextAreaField
              label="Reason for rejection"
              rows={4}
              value={reason}
              maxLength={MAX_REJECTION_REASON}
              error={reasonError}
              hint={`${reason.trim().length}/${MAX_REJECTION_REASON} characters`}
              onChange={(e) => {
                setReason(e.target.value);
                setReasonError(undefined);
              }}
              disabled={saving}
            />
          </div>
        )}

        {error && (
          <Alert tone="error" className="mt-4">
            {error}
          </Alert>
        )}

        <div className="mt-6 grid grid-cols-2 gap-3">
          <Button ref={cancelRef} variant="outline" onClick={cancel} disabled={saving}>
            Cancel
          </Button>
          <Button variant={approving ? 'primary' : 'danger'} onClick={submit} loading={saving}>
            {approving ? 'Approve' : 'Reject'}
          </Button>
        </div>
      </div>
    </div>
  );
}
