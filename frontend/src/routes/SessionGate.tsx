import { WifiOff } from 'lucide-react';
import LoadingState from '../components/feedback/LoadingState';
import StateView from '../components/feedback/StateView';
import Button from '../components/ui/Button';
import { useAuth } from '../hooks/useAuth';

/**
 * Shown by the route guards while the session isn't known yet: "restoring" after a reload, or
 * "offline" when the server couldn't be reached to check it. Returns null once it is known.
 */
export default function SessionGate() {
  const { status, retryRestore } = useAuth();

  if (status === 'restoring') {
    return <LoadingState title="Signing you in..." message="Restoring your session." />;
  }
  if (status === 'offline') {
    return (
      <StateView
        icon={WifiOff}
        tone="warning"
        title="Can't reach SwiftDrop"
        message="We couldn't check your session. Please check your connection and try again."
        actions={<Button onClick={retryRestore}>Try Again</Button>}
      />
    );
  }
  return null;
}
