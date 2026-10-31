import { cn } from '../../utils/cn';
import Spinner from '../ui/Spinner';

interface LoadingStateProps {
  title?: string;
  message?: string;
  className?: string;
}

export default function LoadingState({
  title = 'Loading...',
  message = 'Please wait a moment.',
  className,
}: LoadingStateProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn('flex flex-col items-center px-6 py-12 text-center', className)}
    >
      <Spinner className="size-12 text-brand-600" />
      <p className="mt-5 text-base font-semibold text-slate-900">{title}</p>
      <p className="mt-1 text-sm text-slate-500">{message}</p>
    </div>
  );
}
