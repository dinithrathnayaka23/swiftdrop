import { useId } from 'react';
import { cn } from '../../utils/cn';

interface SwitchProps {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** Extra explanation under the label. */
  description?: string;
  disabled?: boolean;
  /** Danger colour when on (e.g. maintenance mode). */
  tone?: 'brand' | 'danger';
}

/** On/off toggle with a visible "Enabled"/"Disabled" state, as in the System Settings design. */
export default function Switch({
  label,
  checked,
  onChange,
  description,
  disabled,
  tone = 'brand',
}: SwitchProps) {
  const descriptionId = useId();
  return (
    <div>
      <p className="mb-1.5 text-sm font-medium text-slate-700">{label}</p>
      <div className="flex items-start gap-3">
        <button
          type="button"
          role="switch"
          aria-checked={checked}
          aria-label={label}
          aria-describedby={description ? descriptionId : undefined}
          disabled={disabled}
          onClick={() => onChange(!checked)}
          className={cn(
            'relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 disabled:opacity-50',
            checked ? (tone === 'danger' ? 'bg-red-600' : 'bg-brand-600') : 'bg-slate-300',
          )}
        >
          <span
            aria-hidden="true"
            className={cn(
              'inline-block size-5 rounded-full bg-white shadow transition-transform',
              checked ? 'translate-x-6' : 'translate-x-1',
            )}
          />
        </button>
        <div className="pt-0.5">
          <span
            className={cn(
              'text-sm font-medium',
              checked ? (tone === 'danger' ? 'text-red-700' : 'text-brand-700') : 'text-slate-500',
            )}
          >
            {checked ? 'Enabled' : 'Disabled'}
          </span>
          {description && (
            <p id={descriptionId} className="mt-0.5 text-xs text-slate-500">
              {description}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
