import { ChevronDown } from 'lucide-react';
import { useId, type SelectHTMLAttributes } from 'react';
import Field from './Field';
import { describedBy, inputClasses } from './fieldStyles';

export interface SelectOption {
  value: string;
  label: string;
}

interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  label: string;
  options: SelectOption[];
  error?: string;
  hint?: string;
}

/** Labelled dropdown with the same look as TextField. */
export default function SelectField({
  label,
  options,
  error,
  hint,
  className,
  ...rest
}: SelectFieldProps) {
  const id = useId();
  return (
    <Field id={id} label={label} error={error} hint={hint}>
      <div className="relative">
        <select
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(id, error, hint)}
          className={inputClasses(Boolean(error), `h-11 appearance-none pr-10 ${className ?? ''}`)}
          {...rest}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDown
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-slate-500"
          aria-hidden="true"
        />
      </div>
    </Field>
  );
}
