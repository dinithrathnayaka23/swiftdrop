import { useState, type ChangeEvent, type FormEvent } from 'react';
import { ApiRequestError } from '../api/errors';
import { hasErrors, type FieldErrors } from '../utils/validation';

type Values = Record<string, string>;

/**
 * Small form helper with consistent validation behaviour:
 * - a field's error appears once the user leaves it (blur), then updates as they type;
 * - submitting shows every error and focuses the first invalid field;
 * - server-side errors (e.g. "email already exists") show until that field is edited.
 * Fields are focused in the order they appear in `initialValues`.
 */
export function useForm<T extends Values>(
  initialValues: T,
  validate: (values: T) => FieldErrors<Extract<keyof T, string>>,
) {
  type Field = Extract<keyof T, string>;

  const [values, setValues] = useState<T>(initialValues);
  const [touched, setTouched] = useState<Partial<Record<Field, boolean>>>({});
  const [submitted, setSubmitted] = useState(false);
  const [serverErrors, setServerErrors] = useState<FieldErrors<Field>>({});

  const fields = Object.keys(initialValues) as Field[];
  const clientErrors = validate(values);
  const errors: FieldErrors<Field> = {};
  for (const name of fields) {
    const error =
      serverErrors[name] ?? (submitted || touched[name] ? clientErrors[name] : undefined);
    if (error) errors[name] = error;
  }

  function setValue(name: Field, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
    if (serverErrors[name]) setServerErrors((current) => ({ ...current, [name]: undefined }));
  }

  function touch(name: Field) {
    setTouched((current) => (current[name] ? current : { ...current, [name]: true }));
  }

  /** Props for TextField / PasswordField / TextAreaField. */
  function field(name: Field) {
    return {
      name,
      value: values[name],
      error: errors[name],
      onChange: (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
        setValue(name, e.target.value),
      onBlur: () => touch(name),
    };
  }

  function handleSubmit(onValid: (values: T) => void | Promise<void>) {
    return (e: FormEvent<HTMLFormElement>) => {
      e.preventDefault();
      setSubmitted(true);
      const current = validate(values);
      if (hasErrors(current)) {
        const firstInvalid = fields.find((name) => current[name]);
        e.currentTarget.querySelector<HTMLElement>(`[name="${firstInvalid}"]`)?.focus();
        return;
      }
      void onValid(values);
    };
  }

  /**
   * Shows the backend's per-field errors (409 "already exists", 400 validation) on the matching
   * inputs. Returns true if at least one error belonged to a field of this form.
   */
  function applyServerErrors(err: unknown): boolean {
    if (!(err instanceof ApiRequestError)) return false;
    const mapped: FieldErrors<Field> = {};
    for (const { field: name, message } of err.fieldErrors) {
      if ((fields as string[]).includes(name)) mapped[name as Field] = message;
    }
    if (!hasErrors(mapped)) return false;
    setServerErrors(mapped);
    return true;
  }

  return {
    values,
    errors,
    field,
    setValue,
    touch,
    handleSubmit,
    setServerErrors,
    applyServerErrors,
  };
}
