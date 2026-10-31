import { MailCheck } from 'lucide-react';
import { useLocation } from 'react-router';
import ResendVerificationButton from '../../components/auth/ResendVerificationButton';
import StateView from '../../components/feedback/StateView';
import ButtonLink from '../../components/ui/ButtonLink';
import { PATHS } from '../../routes/paths';

export interface CheckEmailState {
  email: string;
}

/** After customer sign-up: the account exists, but the email address must be confirmed first. */
export default function CheckEmailPage() {
  const email = (useLocation().state as CheckEmailState | null)?.email;

  return (
    <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
      <StateView
        icon={MailCheck}
        tone="success"
        title="Confirm your email"
        message={
          email ? (
            <>
              We&apos;ve sent a confirmation link to{' '}
              <span className="font-semibold text-slate-700">{email}</span>. Open it to activate
              your account, then sign in. The link expires in 24 hours; don&apos;t forget to check
              your spam folder.
            </>
          ) : (
            'We’ve sent you a confirmation link. Open it to activate your account, then sign in.'
          )
        }
        actions={
          <div className="flex w-full flex-col items-center gap-3">
            {email && <ResendVerificationButton email={email} justSent />}
            <ButtonLink to={PATHS.login}>Go to Sign In</ButtonLink>
          </div>
        }
      />
    </div>
  );
}
