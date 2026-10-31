import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../../utils/cn';

type Tone = 'neutral' | 'brand' | 'success' | 'danger' | 'warning' | 'info';

const TONES: Record<Tone, string> = {
  neutral: 'bg-slate-100 text-slate-500',
  brand: 'bg-brand-50 text-brand-600',
  success: 'bg-brand-100 text-brand-600',
  danger: 'bg-red-50 text-red-600',
  warning: 'bg-amber-50 text-amber-500',
  info: 'bg-sky-50 text-sky-600',
};

interface StateViewProps {
  icon?: LucideIcon;
  tone?: Tone;
  /** Custom artwork shown instead of the icon circle. */
  illustration?: ReactNode;
  title: string;
  message?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

/**
 * Centered icon + title + message + actions. Used for empty, success, failure,
 * not-found, access-denied and session-expired screens.
 */
export default function StateView({
  icon: Icon,
  tone = 'neutral',
  illustration,
  title,
  message,
  actions,
  className,
}: StateViewProps) {
  return (
    <div className={cn('flex flex-col items-center px-6 py-10 text-center', className)}>
      {illustration ??
        (Icon && (
          <div className={cn('flex size-16 items-center justify-center rounded-full', TONES[tone])}>
            <Icon className="size-8" strokeWidth={1.8} aria-hidden="true" />
          </div>
        ))}
      <h2 className="mt-5 text-lg font-semibold text-slate-900">{title}</h2>
      {message && <p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">{message}</p>}
      {actions && (
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3">{actions}</div>
      )}
    </div>
  );
}
