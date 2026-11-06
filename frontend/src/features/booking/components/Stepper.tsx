import { Check } from 'lucide-react';
import { cn } from '../../../utils/cn';

interface StepperProps {
  steps: readonly { title: string; description: string }[];
  /** The current step, starting at 1. */
  current: number;
}

export default function Stepper({ steps, current }: StepperProps) {
  return (
    <ol className="grid gap-4 sm:grid-cols-3">
      {steps.map((step, index) => {
        const number = index + 1;
        const done = number < current;
        const active = number === current;
        return (
          <li
            key={step.title}
            className="flex items-center gap-3"
            aria-current={active ? 'step' : undefined}
          >
            <span
              className={cn(
                'flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold',
                done || active ? 'bg-brand-500 text-white' : 'bg-slate-100 text-slate-500',
              )}
            >
              {done ? <Check className="size-4" aria-hidden="true" /> : number}
            </span>
            <span className="min-w-0">
              <span
                className={cn(
                  'block text-sm font-semibold',
                  active ? 'text-slate-900' : 'text-slate-600',
                )}
              >
                {step.title}
              </span>
              <span className="block text-xs text-slate-500">{step.description}</span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}
