import { Check } from 'lucide-react';
import { cn } from '../../../utils/cn';
import { formatShortDate, formatTime, type ProgressStep } from '../parcelDisplay';
import { statusVisual } from './statusVisuals';

const STATE_TEXT = { complete: 'completed', current: 'current step', upcoming: 'not reached yet' };

function StepIcon({ step }: { step: ProgressStep }) {
  const Icon = statusVisual(step.code).icon;
  const failed = step.code === 'FAILED';
  return (
    <span
      className={cn(
        'relative z-10 flex size-9 shrink-0 items-center justify-center rounded-full',
        step.state === 'complete' && 'bg-brand-700 text-white',
        step.state === 'current' && !failed && 'bg-blue-600 text-white ring-4 ring-blue-100',
        step.state === 'current' && failed && 'bg-red-600 text-white ring-4 ring-red-100',
        step.state === 'upcoming' && 'border border-slate-200 bg-slate-50 text-slate-400',
      )}
    >
      {step.state === 'complete' ? (
        <Check className="size-4" strokeWidth={3} aria-hidden="true" />
      ) : (
        <Icon className="size-4" aria-hidden="true" />
      )}
    </span>
  );
}

function StepText({ step }: { step: ProgressStep }) {
  return (
    <>
      <p
        className={cn(
          'text-sm font-semibold',
          step.state === 'upcoming' ? 'text-slate-500' : 'text-slate-900',
        )}
      >
        {step.label}
        <span className="sr-only"> ({STATE_TEXT[step.state]})</span>
      </p>
      {step.reachedAt && (
        <p className="mt-0.5 text-xs leading-5 text-slate-500">
          {formatShortDate(step.reachedAt)}
          <br className="hidden sm:inline" />
          <span className="sm:hidden">, </span>
          {formatTime(step.reachedAt)}
        </p>
      )}
    </>
  );
}

/** Created → Picked Up → In Transit → Out for Delivery → Delivered. Vertical on phones. */
export default function DeliveryProgress({ steps }: { steps: ProgressStep[] }) {
  const line = (next: ProgressStep) =>
    next.state === 'upcoming' ? 'border-dashed border-slate-300' : 'border-solid border-brand-700';

  return (
    <>
      <ol className="sm:hidden" aria-label="Delivery progress">
        {steps.map((step, i) => (
          <li key={step.code} className="relative flex gap-4 pb-5 last:pb-0">
            {i < steps.length - 1 && (
              <span
                aria-hidden="true"
                className={cn('absolute top-9 bottom-0 left-[18px] border-l-2', line(steps[i + 1]))}
              />
            )}
            <StepIcon step={step} />
            <div className="pt-1.5">
              <StepText step={step} />
            </div>
          </li>
        ))}
      </ol>

      <ol
        className="hidden sm:grid"
        style={{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }}
        aria-label="Delivery progress"
      >
        {steps.map((step, i) => (
          <li key={step.code} className="relative flex flex-col items-center text-center">
            {i < steps.length - 1 && (
              <span
                aria-hidden="true"
                className={cn('absolute top-[18px] left-1/2 w-full border-t-2', line(steps[i + 1]))}
              />
            )}
            <StepIcon step={step} />
            <div className="mt-3 px-1">
              <StepText step={step} />
            </div>
          </li>
        ))}
      </ol>
    </>
  );
}
