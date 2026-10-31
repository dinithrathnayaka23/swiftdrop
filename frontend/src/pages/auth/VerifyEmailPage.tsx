import { CircleCheck, Hourglass, Link2Off } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError, getErrorMessage } from '../../api/errors';
import LoadingState from '../../components/feedback/LoadingState';
import StateView from '../../components/feedback/StateView';
import ButtonLink from '../../components/ui/ButtonLink';
import { PATHS } from '../../routes/paths';
import type { EmailVerifiedResponse } from '../../types/auth';

type Result =
  | { state: 'verified'; awaitingApproval: boolean }
  | { state: 'invalid' }
  | { state: 'error'; message: string };

// One request per token, even when React runs the effect twice (StrictMode) or the page re-renders.
const confirmations = new Map<string, Promise<EmailVerifiedResponse>>();

function confirmOnce(token: string): Promise<EmailVerifiedResponse> {
  let pending = confirmations.get(token);
  if (!pending) {
    pending = authApi.confirmEmailVerification({ token });
    confirmations.set(token, pending);
    pending.catch(() => confirmations.delete(token)); // allow a retry after a failure
  }
  return pending;
}

function Card({ children }: { children: ReactNode }) {
  return <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">{children}</div>;
}

/** Opened from the confirmation email (/verify-email?token=...). Confirms straight away. */
export default function VerifyEmailPage() {
  const token = useSearchParams()[0].get('token') ?? '';
  const [result, setResult] = useState<Result | null>(null);

  useEffect(() => {
    if (!token) return;
    let active = true;
    confirmOnce(token).then(
      (response) => {
        if (active) setResult({ state: 'verified', awaitingApproval: response.awaitingApproval });
      },
      (err: unknown) => {
        if (!active) return;
        setResult(
          err instanceof ApiRequestError && err.code === 'INVALID_VERIFICATION_TOKEN'
            ? { state: 'invalid' }
            : { state: 'error', message: getErrorMessage(err) },
        );
      },
    );
    return () => {
      active = false;
    };
  }, [token]);

  if (!token || result?.state === 'invalid') {
    return (
      <Card>
        <StateView
          icon={Link2Off}
          tone="danger"
          title="Link invalid or expired"
          message="This confirmation link is invalid or has expired. Sign in with your email and password to get a new one."
          actions={<ButtonLink to={PATHS.login}>Go to Sign In</ButtonLink>}
        />
      </Card>
    );
  }

  if (!result) {
    return (
      <Card>
        <LoadingState title="Confirming your email..." message="This only takes a moment." />
      </Card>
    );
  }

  if (result.state === 'error') {
    return (
      <Card>
        <StateView
          icon={Link2Off}
          tone="warning"
          title="We couldn't confirm your email"
          message={`${result.message} Please open the link again in a moment.`}
          actions={<ButtonLink to={PATHS.home}>Back to Home</ButtonLink>}
        />
      </Card>
    );
  }

  return (
    <Card>
      {result.awaitingApproval ? (
        <StateView
          icon={Hourglass}
          tone="warning"
          title="Email confirmed"
          message="Thanks! Your merchant account is now waiting for approval. We'll email you as soon as it's approved."
          actions={<ButtonLink to={PATHS.home}>Back to Home</ButtonLink>}
        />
      ) : (
        <StateView
          icon={CircleCheck}
          tone="success"
          title="Email confirmed"
          message="Your account is ready. You can now sign in."
          actions={<ButtonLink to={PATHS.login}>Sign In</ButtonLink>}
        />
      )}
    </Card>
  );
}
