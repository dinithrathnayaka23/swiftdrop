import { CircleCheck, Link2Off } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError, getErrorMessage } from '../../api/errors';
import AuthCard from '../../components/auth/AuthCard';
import StateView from '../../components/feedback/StateView';
import Alert from '../../components/ui/Alert';
import Button from '../../components/ui/Button';
import ButtonLink from '../../components/ui/ButtonLink';
import PasswordField from '../../components/ui/PasswordField';
import { useForm } from '../../hooks/useForm';
import { PATHS } from '../../routes/paths';
import { validateNewPassword, validatePasswordConfirmation } from '../../utils/validation';

function ResultCard({ children }: { children: ReactNode }) {
  return <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">{children}</div>;
}

/** Opened from the reset email (/reset-password?token=...). The token is single-use and expires. */
export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const form = useForm({ newPassword: '', confirmPassword: '' }, (v) => ({
    newPassword: validateNewPassword(v.newPassword),
    confirmPassword: validatePasswordConfirmation(v.newPassword, v.confirmPassword),
  }));
  const [formError, setFormError] = useState<string | null>(null);
  const [linkInvalid, setLinkInvalid] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const onSubmit = form.handleSubmit(async ({ newPassword }) => {
    setSubmitting(true);
    setFormError(null);
    try {
      await authApi.confirmPasswordReset({ token, newPassword });
      setDone(true);
    } catch (err) {
      if (err instanceof ApiRequestError && err.code === 'INVALID_RESET_TOKEN') {
        setLinkInvalid(true);
      } else if (!form.applyServerErrors(err)) {
        setFormError(getErrorMessage(err));
      }
    } finally {
      setSubmitting(false);
    }
  });

  if (done) {
    return (
      <ResultCard>
        <StateView
          icon={CircleCheck}
          tone="success"
          title="Password updated"
          message="Your password has been changed. You can now sign in with your new password."
          actions={<ButtonLink to={PATHS.login}>Sign In</ButtonLink>}
        />
      </ResultCard>
    );
  }

  if (!token || linkInvalid) {
    return (
      <ResultCard>
        <StateView
          icon={Link2Off}
          tone="danger"
          title="Link invalid or expired"
          message="This password reset link is invalid, has expired or was already used. Please request a new one."
          actions={<ButtonLink to={PATHS.forgotPassword}>Request a New Link</ButtonLink>}
        />
      </ResultCard>
    );
  }

  return (
    <AuthCard
      title="Choose a new password"
      subtitle="Enter a new password for your SwiftDrop account."
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
        <PasswordField
          label="New Password"
          autoComplete="new-password"
          placeholder="Create a new password"
          showStrength
          {...form.field('newPassword')}
        />
        <PasswordField
          label="Confirm New Password"
          autoComplete="new-password"
          placeholder="Repeat the new password"
          {...form.field('confirmPassword')}
        />
        <Button type="submit" fullWidth loading={submitting}>
          {submitting ? 'Updating...' : 'Update Password'}
        </Button>
      </form>
    </AuthCard>
  );
}
