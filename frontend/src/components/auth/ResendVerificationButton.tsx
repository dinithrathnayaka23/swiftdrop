import { useEffect, useState } from 'react';
import { authApi } from '../../api/auth';
import { getErrorMessage } from '../../api/errors';
import Alert from '../ui/Alert';
import Button from '../ui/Button';

/** Matches the backend's email-verification resend cooldown (one email per address per minute). */
const RESEND_COOLDOWN_SECONDS = 60;

/** When a new email may be requested (ms timestamp). Called from event handlers only. */
function cooldownEndsAt(): { sentAt: number; endsAt: number } {
  const sentAt = Date.now();
  return { sentAt, endsAt: sentAt + RESEND_COOLDOWN_SECONDS * 1000 };
}

interface ResendVerificationButtonProps {
  email: string;
  /** True right after sign-up: the first email was just sent, so start with the countdown. */
  justSent?: boolean;
}

/** "Resend confirmation email" with a countdown, a success note and error handling. */
export default function ResendVerificationButton({
  email,
  justSent = false,
}: ResendVerificationButtonProps) {
  const [resendAt, setResendAt] = useState(() => (justSent ? cooldownEndsAt().endsAt : 0));
  const [now, setNow] = useState(() => Date.now());
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const secondsLeft = Math.max(0, Math.ceil((resendAt - now) / 1000));

  // Tick once a second while the countdown is running.
  useEffect(() => {
    if (resendAt === 0) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [resendAt]);

  async function resend() {
    setSending(true);
    setError(null);
    try {
      await authApi.resendVerification({ email });
      const { sentAt, endsAt } = cooldownEndsAt();
      setNow(sentAt);
      setResendAt(endsAt);
      setSent(true);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="flex w-full flex-col items-center gap-3">
      {error && <Alert tone="error">{error}</Alert>}
      {sent && !error && (
        <p role="status" className="text-sm font-medium text-brand-700">
          We sent a new confirmation link.
        </p>
      )}
      <Button variant="outline" onClick={resend} loading={sending} disabled={secondsLeft > 0}>
        {secondsLeft > 0
          ? `Resend confirmation email in ${secondsLeft}s`
          : 'Resend confirmation email'}
      </Button>
    </div>
  );
}
