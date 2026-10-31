import { MailCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { authApi } from '../../api/auth';
import { getErrorMessage } from '../../api/errors';
import AuthCard from '../../components/auth/AuthCard';
import StateView from '../../components/feedback/StateView';
import Alert from '../../components/ui/Alert';
import Button from '../../components/ui/Button';
import ButtonLink from '../../components/ui/ButtonLink';
import TextField from '../../components/ui/TextField';
import { useForm } from '../../hooks/useForm';
import { PATHS } from '../../routes/paths';
import { loadLoginPreferences } from '../../utils/loginPreferences';
import { validateEmail } from '../../utils/validation';

/** Matches the backend's PASSWORD_RESET_RESEND_COOLDOWN (one email per address per minute). */
export const RESEND_COOLDOWN_SECONDS = 60;

/** When a new reset email may be requested (ms timestamp). Called from event handlers only. */
function cooldownEndsAt(): { sentAt: number; endsAt: number } {
  const sentAt = Date.now();
  return { sentAt, endsAt: sentAt + RESEND_COOLDOWN_SECONDS * 1000 };
}

export default function ForgotPasswordPage() {
  const form = useForm({ email: loadLoginPreferences()?.email ?? '' }, (v) => ({
    email: validateEmail(v.email),
  }));
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [resendAt, setResendAt] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const [resent, setResent] = useState(false);

  // Tick once a second while the "Resend" countdown is running.
  useEffect(() => {
    if (!sentTo) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [sentTo]);

  const secondsLeft = Math.max(0, Math.ceil((resendAt - now) / 1000));

  async function sendLink(email: string) {
    setSubmitting(true);
    setFormError(null);
    try {
      await authApi.requestPasswordReset({ email });
      const { sentAt, endsAt } = cooldownEndsAt();
      setSentTo(email);
      setNow(sentAt);
      setResendAt(endsAt);
      return true;
    } catch (err) {
      setFormError(getErrorMessage(err));
      return false;
    } finally {
      setSubmitting(false);
    }
  }

  const onSubmit = form.handleSubmit(async ({ email }) => {
    setResent(false);
    await sendLink(email.trim());
  });

  async function resend() {
    if (sentTo && (await sendLink(sentTo))) setResent(true);
  }

  if (sentTo) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <StateView
          icon={MailCheck}
          tone="success"
          title="Check your email"
          // Same message whether or not the account exists, so emails can't be enumerated.
          message={
            <>
              If an account exists for{' '}
              <span className="font-semibold text-slate-700">{sentTo}</span>, we&apos;ve sent a link
              to reset your password. It expires in 30 minutes. Don&apos;t forget to check your spam
              folder.
            </>
          }
          actions={
            <div className="flex w-full flex-col items-center gap-3">
              {formError && <Alert tone="error">{formError}</Alert>}
              {resent && !formError && (
                <p role="status" className="text-sm font-medium text-brand-700">
                  We sent the link again.
                </p>
              )}
              <div className="flex flex-wrap justify-center gap-3">
                <Button
                  variant="outline"
                  onClick={resend}
                  loading={submitting}
                  disabled={secondsLeft > 0}
                >
                  {secondsLeft > 0 ? `Resend email in ${secondsLeft}s` : 'Resend email'}
                </Button>
                <ButtonLink to={PATHS.login}>Back to Sign In</ButtonLink>
              </div>
              <button
                type="button"
                onClick={() => {
                  setSentTo(null);
                  setResent(false);
                  setFormError(null);
                }}
                className="text-sm font-medium text-brand-600 hover:text-brand-700"
              >
                Use a different email
              </button>
            </div>
          }
        />
      </div>
    );
  }

  return (
    <AuthCard
      backTo={PATHS.login}
      backLabel="Back to Sign In"
      title="Forgot Password?"
      subtitle="Enter your email and we'll send you a reset link."
      footer={
        <>
          Remembered it?{' '}
          <Link to={PATHS.login} className="font-semibold text-brand-600 hover:text-brand-700">
            Sign In
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          maxLength={255}
          {...form.field('email')}
        />
        <Button type="submit" fullWidth loading={submitting}>
          Send Reset Link
        </Button>
      </form>
    </AuthCard>
  );
}
