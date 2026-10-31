import { Check } from 'lucide-react';
import { cn } from '../../utils/cn';
import { formatShortDateTime } from '../../utils/date';
import type { StepState, TimelineStep } from '../../utils/parcelStatus';

function StepDot({ state }: { state: StepState }) {
  return (
    <span
      className={cn(
        'relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full',
        state === 'complete' && 'bg-brand-600 text-white',
        state === 'current' && 'bg-blue-600 text-white ring-4 ring-blue-100',
        state === 'upcoming' && 'border-2 border-slate-200 bg-white',
      )}
    >
      {state === 'upcoming' ? (
        <span className="size-2 rounded-full bg-slate-300" />
      ) : (
        <Check className="size-4" strokeWidth={3} aria-hidden="true" />
      )}
    </span>
  );
}

function lineClass(next: TimelineStep): string {
  return next.state === 'upcoming' ? 'bg-slate-200' : 'bg-brand-500';
}

const STATE_TEXT: Record<StepState, string> = {
  complete: 'completed',
  current: 'current step',
  upcoming: 'not reached yet',
};

/** Picked Up → In Transit → Out for Delivery → Delivered. Horizontal on sm+, vertical on phones. */
export default function TrackingTimeline({ steps }: { steps: TimelineStep[] }) {
  return (
    <>
      {/* phones: vertical */}
      <ol className="sm:hidden" aria-label="Delivery progress">
        {steps.map((step, i) => (
          <li key={step.code} className="relative flex gap-4 pb-6 last:pb-0">
            {i < steps.length - 1 && (
              <span
                aria-hidden="true"
                className={cn(
                  'absolute top-8 bottom-0 left-4 w-0.5 -translate-x-1/2',
                  lineClass(steps[i + 1]),
                )}
              />
            )}
            <StepDot state={step.state} />
            <div className="pt-1">
              <p
                className={cn(
                  'text-sm font-semibold',
                  step.state === 'upcoming' ? 'text-slate-400' : 'text-slate-900',
                )}
              >
                {step.label}
                <span className="sr-only"> ({STATE_TEXT[step.state]})</span>
              </p>
              {step.reachedAt && (
                <p className="mt-0.5 text-xs text-slate-500">
                  {formatShortDateTime(step.reachedAt)}
                </p>
              )}
            </div>
          </li>
        ))}
      </ol>

      {/* sm+: horizontal */}
      <ol className="hidden grid-cols-4 sm:grid" aria-label="Delivery progress">
        {steps.map((step, i) => (
          <li key={step.code} className="relative">
            {i < steps.length - 1 && (
              <span
                aria-hidden="true"
                className={cn(
                  'absolute top-4 left-4 h-0.5 w-full -translate-y-1/2',
                  lineClass(steps[i + 1]),
                )}
              />
            )}
            <StepDot state={step.state} />
            <p
              className={cn(
                'mt-3 pr-2 text-sm font-semibold',
                step.state === 'upcoming' ? 'text-slate-400' : 'text-slate-900',
              )}
            >
              {step.label}
              <span className="sr-only"> ({STATE_TEXT[step.state]})</span>
            </p>
            {step.reachedAt && (
              <p className="mt-0.5 pr-2 text-xs text-slate-500">
                {formatShortDateTime(step.reachedAt)}
              </p>
            )}
          </li>
        ))}
      </ol>
    </>
  );
}
