import type { StatusHistoryItem } from '../../../types/parcel';
import { cn } from '../../../utils/cn';
import { formatDate, formatTime, historyText } from '../parcelDisplay';
import { statusVisual, TONE_TILE } from './statusVisuals';

const DOT: Record<string, string> = {
  neutral: 'bg-slate-400',
  info: 'bg-blue-500',
  success: 'bg-brand-600',
  warning: 'bg-amber-500',
  danger: 'bg-red-500',
};

/** A parcel's status changes, newest first. */
export default function StatusHistoryList({ history }: { history: StatusHistoryItem[] }) {
  const newestFirst = [...history].reverse();
  return (
    <ol className="relative">
      {newestFirst.map((item, i) => {
        const visual = statusVisual(item.status);
        const Icon = visual.icon;
        return (
          <li
            key={`${item.status}-${item.changedAt}`}
            className="relative flex gap-4 border-b border-slate-100 py-4 pl-6 last:border-b-0"
          >
            {i < newestFirst.length - 1 && (
              <span
                aria-hidden="true"
                className="absolute top-8 -bottom-4 left-[3px] w-px bg-slate-200"
              />
            )}
            <span
              aria-hidden="true"
              className={cn('absolute top-7 left-0 size-2 rounded-full', DOT[visual.tone])}
            />
            <span
              className={cn(
                'flex size-10 shrink-0 items-center justify-center rounded-full',
                TONE_TILE[visual.tone],
              )}
            >
              <Icon className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-slate-900">{visual.label}</p>
              <p className="mt-0.5 text-sm text-slate-500">{historyText(item)}</p>
            </div>
            <p className="shrink-0 text-right text-sm text-slate-600">
              {formatDate(item.changedAt)}
              <br />
              <span className="text-slate-500">{formatTime(item.changedAt)}</span>
            </p>
          </li>
        );
      })}
    </ol>
  );
}
