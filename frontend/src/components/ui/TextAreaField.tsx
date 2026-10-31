import { useId, type TextareaHTMLAttributes } from 'react';
import Field from './Field';
import { describedBy, inputClasses } from './fieldStyles';

interface TextAreaFieldProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: string;
  error?: string;
  hint?: string;
  optional?: boolean;
}

export default function TextAreaField({
  label,
  error,
  hint,
  optional,
  rows = 3,
  ...rest
}: TextAreaFieldProps) {
  const id = useId();
  return (
    <Field id={id} label={label} error={error} hint={hint} optional={optional}>
      <textarea
        id={id}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        className={inputClasses(Boolean(error), 'resize-y py-2.5')}
        {...rest}
      />
    </Field>
  );
}
