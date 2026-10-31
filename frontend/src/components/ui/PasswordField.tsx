import { Eye, EyeOff } from 'lucide-react';
import { useId, useState, type InputHTMLAttributes } from 'react';
import Field from './Field';
import { describedBy, inputClasses } from './fieldStyles';
import PasswordStrengthMeter from './PasswordStrengthMeter';

interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> {
  label: string;
  error?: string;
  hint?: string;
  /** For new passwords: show the strength bar and requirement checklist under the input. */
  showStrength?: boolean;
}

export default function PasswordField({
  label,
  error,
  hint,
  showStrength = false,
  ...rest
}: PasswordFieldProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const Icon = visible ? EyeOff : Eye;
  const meterId = `${id}-strength`;
  const describedByIds =
    [describedBy(id, error, hint), showStrength ? meterId : undefined].filter(Boolean).join(' ') ||
    undefined;

  return (
    <Field id={id} label={label} error={error} hint={hint}>
      <div className="relative">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedByIds}
          className={inputClasses(Boolean(error), 'h-11 pr-11')}
          {...rest}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-lg text-slate-400 hover:text-slate-600 focus-visible:text-brand-600 focus-visible:outline-none"
        >
          <Icon className="size-4.5" aria-hidden="true" />
        </button>
      </div>
      {showStrength && <PasswordStrengthMeter id={meterId} password={String(rest.value ?? '')} />}
    </Field>
  );
}
