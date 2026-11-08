import { Hourglass } from 'lucide-react';
import { useEffect, useState } from 'react';
import StateView from '../../../components/feedback/StateView';
import Button from '../../../components/ui/Button';

/** "30 seconds", "1 second", "2 minutes": the same wording as the backend's 429 message. */
function formatWait(seconds: number): string {
  if (seconds < 60) return seconds === 1 ? '1 second' : `${seconds} seconds`;
  const minutes = Math.ceil(seconds / 60);
  return minutes === 1 ? '1 minute' : `${minutes} minutes`;
}

/** Seconds left until `seconds` from the first render have passed; counts down to 0. */
function useCountdown(seconds: number): number {
  const [endsAt] = useState(() => Date.now() + seconds * 1000);
  const [left, setLeft] = useState(seconds);

  useEffect(() => {
    if (left <= 0) return;
    const timer = setTimeout(
      () => setLeft(Math.max(0, Math.ceil((endsAt - Date.now()) / 1000))),
      1000,
    );
    return () => clearTimeout(timer);
  }, [left, endsAt]);

  return left;
}

interface RateLimitedViewProps {
  retryAfterSeconds: number;
  onRetry: () => void;
}

/** 429 from public tracking: too many searches from this connection; retry once the wait is over. */
export default function RateLimitedView({ retryAfterSeconds, onRetry }: RateLimitedViewProps) {
  const left = useCountdown(retryAfterSeconds);
  const ready = left <= 0;

  return (
    <StateView
      className="py-14"
      icon={Hourglass}
      tone="warning"
      title="Too Many Searches"
      message={
        ready
          ? 'Thanks for waiting. You can search again now.'
          : `You've made a lot of searches in a short time. Please try again in ${formatWait(left)}.`
      }
      actions={
        <Button variant="outline" onClick={onRetry} disabled={!ready}>
          {ready ? 'Try Again' : `Try again in ${formatWait(left)}`}
        </Button>
      }
    />
  );
}
