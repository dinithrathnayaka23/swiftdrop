import { Clock } from 'lucide-react';
import StateView from '../../components/feedback/StateView';
import { useLocation } from 'react-router';
import ButtonLink from '../../components/ui/ButtonLink';
import { PATHS } from '../../routes/paths';

/** Shown when the backend rejects an expired or invalid token. */
export default function SessionExpiredPage() {
  const signInPath =
    (useLocation().state as { signInPath?: string } | null)?.signInPath ?? PATHS.login;
  return (
    <div className="flex min-h-[60vh] items-center justify-center px-4 py-12">
      <StateView
        icon={Clock}
        tone="neutral"
        title="Session Expired"
        message="Your session has expired. Please sign in again to continue."
        actions={<ButtonLink to={signInPath}>Sign In</ButtonLink>}
      />
    </div>
  );
}
