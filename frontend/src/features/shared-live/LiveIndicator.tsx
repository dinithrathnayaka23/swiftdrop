import type { LiveConnectionState } from './types';
import { cn } from '../../utils/cn';

const LOOK: Record<LiveConnectionState, { dot: string; text: string; label: string }> = {
  live: { dot: 'bg-brand-500', text: 'text-brand-700', label: 'Live' },
  connecting: { dot: 'bg-amber-400', text: 'text-amber-700', label: 'Connecting…' },
  offline: { dot: 'bg-slate-400', text: 'text-slate-500', label: 'Live updates paused' },
};

/** Whether status changes are arriving live. Announced politely when it changes. */
export default function LiveIndicator({ state }: { state: LiveConnectionState }) {
  const look = LOOK[state];
  return (
    <span
      role="status"
      aria-live="polite"
      className={cn('inline-flex items-center gap-1.5 text-xs font-semibold', look.text)}
    >
      <span className="relative flex size-2" aria-hidden="true">
        {state === 'live' && (
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-brand-400 opacity-60" />
        )}
        <span className={cn('relative inline-flex size-2 rounded-full', look.dot)} />
      </span>
      {look.label}
    </span>
  );
}
