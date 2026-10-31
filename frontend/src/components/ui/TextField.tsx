import { useId, type InputHTMLAttributes } from 'react';
import Field from './Field';
import { describedBy, inputClasses } from './fieldStyles';

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  error?: string;
  hint?: string;
  optional?: boolean;
}

export default function TextField({
  label,
  error,
  hint,
  optional,
  className,
  ...rest
}: TextFieldProps) {
  const id = useId();
  return (
    <Field id={id} label={label} error={error} hint={hint} optional={optional}>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        className={inputClasses(Boolean(error), `h-11 ${className ?? ''}`)}
        {...rest}
      />
    </Field>
  );
}
