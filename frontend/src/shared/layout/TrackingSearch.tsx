import { Search } from 'lucide-react';
import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { generatePath, useNavigate } from 'react-router';
import { PATHS } from '../../routes/paths';
import { cn } from '../../utils/cn';
import { validateTrackingNumber } from '../../utils/validation';

/**
 * Top bar search: type a tracking number and press Enter to open its tracking page.
 * Ctrl+K (Cmd+K on a Mac) jumps to it from anywhere.
 */
export default function TrackingSearch({ className }: { className?: string }) {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const errorId = useId();
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const problem = validateTrackingNumber(value);
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    setValue('');
    inputRef.current?.blur();
    navigate(generatePath(PATHS.trackResult, { trackingNumber: value.trim().toUpperCase() }));
  };

  return (
    <form role="search" onSubmit={onSubmit} noValidate className={cn('relative', className)}>
      <Search
        className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400"
        aria-hidden="true"
      />
      <input
        ref={inputRef}
        type="search"
        aria-label="Track a parcel by tracking number"
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        placeholder="Search by tracking number..."
        value={value}
        onChange={(e) => {
          setValue(e.target.value);
          if (error) setError(null);
        }}
        onBlur={() => setError(null)}
        className={cn(
          'h-10 w-full rounded-lg border bg-slate-50 pr-14 pl-9 text-sm text-slate-900 placeholder:text-slate-400 focus:bg-white focus:ring-2 focus:outline-none',
          error
            ? 'border-red-300 focus:ring-red-100'
            : 'border-slate-200 focus:border-brand-500 focus:ring-brand-100',
        )}
      />
      <kbd className="pointer-events-none absolute top-1/2 right-2.5 hidden -translate-y-1/2 rounded border border-slate-200 bg-white px-1.5 py-0.5 font-sans text-[11px] text-slate-500 sm:block">
        Ctrl K
      </kbd>
      {error && (
        <p
          id={errorId}
          role="alert"
          className="absolute top-full left-0 z-10 mt-1 rounded-md border border-red-200 bg-white px-3 py-1.5 text-xs text-red-600 shadow-sm"
        >
          {error}
        </p>
      )}
    </form>
  );
}
