import { Check, Circle } from 'lucide-react';
import { cn } from '../../utils/cn';
import {
  getPasswordStrength,
  passwordRequirements,
  type StrengthLevel,
} from '../../utils/passwordStrength';

const LEVEL_STYLES: Record<StrengthLevel, { bar: string; text: string }> = {
  0: { bar: 'bg-red-500', text: 'text-red-600' },
  1: { bar: 'bg-red-500', text: 'text-red-600' },
  2: { bar: 'bg-amber-500', text: 'text-amber-600' },
  3: { bar: 'bg-lime-500', text: 'text-lime-700' },
  4: { bar: 'bg-brand-600', text: 'text-brand-700' },
};

/** Strength bar + requirement checklist shown under a new-password input. */
export default function PasswordStrengthMeter({ password, id }: { password: string; id?: string }) {
  const requirements = passwordRequirements(password);
  const strength = getPasswordStrength(password);
  const style = LEVEL_STYLES[strength.level];
  // "Too short" still lights one red segment so something visibly changes from the first key.
  const filled = password ? Math.max(strength.level, 1) : 0;

  return (
    <div id={id} className="mt-2.5 space-y-2.5">
      {password && (
        <div aria-live="polite">
          <div className="flex gap-1.5" aria-hidden="true">
            {[1, 2, 3, 4].map((segment) => (
              <span
                key={segment}
                className={cn(
                  'h-1.5 flex-1 rounded-full transition-colors',
                  segment <= filled ? style.bar : 'bg-slate-200',
                )}
              />
            ))}
          </div>
          <p className="mt-1.5 text-xs">
            <span className="text-slate-500">Password strength: </span>
            <span className={cn('font-semibold', style.text)}>{strength.label}</span>
          </p>
          {strength.tip && <p className="mt-0.5 text-xs text-slate-500">{strength.tip}</p>}
        </div>
      )}

      <ul className="grid grid-cols-1 gap-x-4 gap-y-1 text-xs sm:grid-cols-2">
        {requirements.map(({ label, met }) => (
          <li
            key={label}
            className={cn('flex items-center gap-1.5', met ? 'text-brand-700' : 'text-slate-500')}
          >
            {met ? (
              <Check className="size-3.5 shrink-0" strokeWidth={3} aria-hidden="true" />
            ) : (
              <Circle className="size-3 shrink-0" aria-hidden="true" />
            )}
            {label}
            <span className="sr-only">{met ? '(done)' : '(not yet)'}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
