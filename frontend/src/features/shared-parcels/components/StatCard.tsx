import { ChevronRight, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';
import { cn } from '../../../utils/cn';
import type { StatusTone } from '../../../utils/parcelStatus';
import { TONE_TILE } from './statusVisuals';

interface StatCardProps {
  label: string;
  value: number | string;
  caption: string;
  icon: LucideIcon;
  tone: StatusTone;
  /** Makes the whole card a link with a chevron. */
  to?: string;
  loading?: boolean;
}

/** Count card at the top of the dashboard and list pages. */
export default function StatCard({
  label,
  value,
  caption,
  icon: Icon,
  tone,
  to,
  loading,
}: StatCardProps) {
  const body = (
    <>
      <span
        className={cn(
          'flex size-14 shrink-0 items-center justify-center rounded-2xl',
          TONE_TILE[tone],
        )}
      >
        <Icon className="size-7" strokeWidth={1.8} aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-slate-700">{label}</span>
        <span className="mt-1 block text-2xl font-bold text-slate-900">
          {loading ? (
            <span className="inline-block h-7 w-8 animate-pulse rounded bg-slate-200" />
          ) : (
            value
          )}
        </span>
        <span className="mt-0.5 block truncate text-sm text-slate-500">{caption}</span>
      </span>
      {to && <ChevronRight className="size-5 self-start text-slate-400" aria-hidden="true" />}
    </>
  );

  const className =
    'flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs transition-colors';
  return to ? (
    <Link to={to} className={cn(className, 'hover:border-brand-200 hover:bg-brand-50/30')}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}
