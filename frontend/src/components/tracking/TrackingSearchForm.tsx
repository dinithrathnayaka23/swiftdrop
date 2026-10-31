import { ArrowRight, Search } from 'lucide-react';
import { useNavigate } from 'react-router';
import { useForm } from '../../hooks/useForm';
import { PATHS } from '../../routes/paths';
import { cn } from '../../utils/cn';
import { validateTrackingNumber } from '../../utils/validation';
import Button from '../ui/Button';

interface TrackingSearchFormProps {
  initialValue?: string;
  autoFocus?: boolean;
  className?: string;
}

/** Tracking number input + "Track Parcel" button; navigates to /track/{number}. */
export default function TrackingSearchForm({
  initialValue = '',
  autoFocus,
  className,
}: TrackingSearchFormProps) {
  const navigate = useNavigate();
  const form = useForm({ trackingNumber: initialValue }, (v) => ({
    trackingNumber: validateTrackingNumber(v.trackingNumber),
  }));
  const { value, error, onChange } = form.field('trackingNumber');

  const onSubmit = form.handleSubmit(({ trackingNumber }) => {
    navigate(`${PATHS.track}/${encodeURIComponent(trackingNumber.trim().toUpperCase())}`);
  });

  return (
    <form onSubmit={onSubmit} noValidate className={className}>
      <div
        className={cn(
          'flex flex-col gap-2 rounded-xl border bg-white p-2 shadow-sm sm:flex-row sm:items-center',
          error ? 'border-red-300' : 'border-slate-200',
        )}
      >
        <div className="relative flex-1">
          <Search
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400"
            aria-hidden="true"
          />
          {/* Validated on submit only: an empty box isn't an error until the user searches. */}
          <input
            name="trackingNumber"
            value={value}
            onChange={onChange}
            placeholder="Enter tracking number (e.g. SD123456789)"
            aria-label="Tracking number"
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? 'tracking-number-error' : undefined}
            autoFocus={autoFocus}
            autoComplete="off"
            spellCheck={false}
            maxLength={40}
            className="h-11 w-full rounded-lg bg-transparent pr-3 pl-9 text-sm text-slate-900 uppercase placeholder:text-slate-400 placeholder:normal-case focus:outline-none"
          />
        </div>
        <Button type="submit" size="md" className="sm:px-6">
          Track Parcel
          <ArrowRight className="size-4" aria-hidden="true" />
        </Button>
      </div>
      {error && (
        <p
          id="tracking-number-error"
          role="alert"
          className="mt-2 text-left text-xs font-medium text-red-600"
        >
          {error}
        </p>
      )}
    </form>
  );
}
