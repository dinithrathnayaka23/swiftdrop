import { ChevronDown } from 'lucide-react';
import { useId } from 'react';
import { cn } from '../../utils/cn';
import { COUNTRY_CODES, countryFor } from '../../utils/phone';
import Field from './Field';
import { describedBy } from './fieldStyles';

interface PhoneFieldProps {
  label: string;
  /** Name of the number input (used to focus it when invalid). */
  name?: string;
  countryCode: string;
  number: string;
  onCountryCodeChange: (code: string) => void;
  onNumberChange: (number: string) => void;
  onBlur?: () => void;
  error?: string;
  hint?: string;
}

/** Country code selector + local number, shown as one control. */
export default function PhoneField({
  label,
  name,
  countryCode,
  number,
  onCountryCodeChange,
  onNumberChange,
  onBlur,
  error,
  hint,
}: PhoneFieldProps) {
  const id = useId();
  const placeholder = countryFor(countryCode)?.example;

  return (
    <Field id={id} label={label} error={error} hint={hint}>
      <div
        className={cn(
          'flex h-11 overflow-hidden rounded-lg border bg-white shadow-xs transition focus-within:ring-4',
          error
            ? 'border-red-400 focus-within:border-red-500 focus-within:ring-red-100'
            : 'border-slate-300 focus-within:border-brand-500 focus-within:ring-brand-100',
        )}
      >
        <div className="relative shrink-0 border-r border-slate-200 bg-slate-50">
          <select
            aria-label="Country code"
            value={countryCode}
            onChange={(e) => onCountryCodeChange(e.target.value)}
            className="h-full appearance-none bg-transparent pr-8 pl-3 text-sm font-medium text-slate-700 focus:outline-none"
          >
            {COUNTRY_CODES.map((c) => (
              <option key={c.code} value={c.code} title={c.country}>
                {c.code} {c.iso}
              </option>
            ))}
          </select>
          <ChevronDown
            className="pointer-events-none absolute top-1/2 right-2.5 size-4 -translate-y-1/2 text-slate-400"
            aria-hidden="true"
          />
        </div>
        <input
          id={id}
          name={name}
          type="tel"
          inputMode="tel"
          autoComplete="tel-national"
          maxLength={16}
          value={number}
          onChange={(e) => onNumberChange(e.target.value)}
          onBlur={onBlur}
          placeholder={placeholder}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(id, error, hint)}
          className="min-w-0 flex-1 bg-transparent px-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none"
        />
      </div>
    </Field>
  );
}
