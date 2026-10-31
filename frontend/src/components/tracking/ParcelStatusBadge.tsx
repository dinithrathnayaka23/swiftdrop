import type { ParcelStatusCode } from '../../types/tracking';
import { cn } from '../../utils/cn';
import { STATUS_META, type StatusTone } from '../../utils/parcelStatus';

const TONES: Record<StatusTone, { badge: string; dot: string }> = {
  neutral: { badge: 'bg-slate-100 text-slate-700', dot: 'bg-slate-400' },
  info: { badge: 'bg-blue-50 text-blue-700', dot: 'bg-blue-500' },
  success: { badge: 'bg-brand-50 text-brand-700', dot: 'bg-brand-500' },
  warning: { badge: 'bg-amber-50 text-amber-700', dot: 'bg-amber-500' },
  danger: { badge: 'bg-red-50 text-red-700', dot: 'bg-red-500' },
};

export default function ParcelStatusBadge({ status }: { status: ParcelStatusCode }) {
  const meta = STATUS_META[status];
  const tone = TONES[meta.tone];
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold',
        tone.badge,
      )}
    >
      <span className={cn('size-1.5 rounded-full', tone.dot)} aria-hidden="true" />
      {meta.label}
    </span>
  );
}
