import { useState, type FormEvent } from 'react';
import { assistantApi } from '../../../api/assistant';
import { toApiRequestError } from '../../../api/errors';
import Button from '../../../components/ui/Button';
import SelectField from '../../../components/ui/SelectField';
import TextField from '../../../components/ui/TextField';
import { DISTRICTS } from '../../booking/districts';
import type { AssistantFeeRequest, AssistantFeeResponse } from '../types';

const DISTRICT_OPTIONS = [
  { value: '', label: 'Select a district' },
  ...DISTRICTS.map((district) => ({ value: district, label: district })),
];

type FieldName =
  | 'pickupDistrict'
  | 'pickupCity'
  | 'dropoffDistrict'
  | 'dropoffCity'
  | 'weightKg'
  | 'lengthCm'
  | 'widthCm'
  | 'heightCm';
type Values = Record<FieldName, string>;

const EMPTY: Values = {
  pickupDistrict: '',
  pickupCity: '',
  dropoffDistrict: '',
  dropoffCity: '',
  weightKg: '',
  lengthCm: '',
  widthCm: '',
  heightCm: '',
};

function validate(values: Values): Partial<Record<FieldName, string>> {
  const errors: Partial<Record<FieldName, string>> = {};
  if (!values.pickupDistrict) errors.pickupDistrict = 'Choose a district';
  if (!values.pickupCity.trim()) errors.pickupCity = 'Enter a city';
  if (!values.dropoffDistrict) errors.dropoffDistrict = 'Choose a district';
  if (!values.dropoffCity.trim()) errors.dropoffCity = 'Enter a city';
  if (!(Number(values.weightKg) > 0)) errors.weightKg = 'Enter a weight above 0';
  for (const size of ['lengthCm', 'widthCm', 'heightCm'] as const) {
    if (values[size] && !(Number(values[size]) > 0)) errors[size] = 'Above 0';
  }
  return errors;
}

const optionalNumber = (value: string) => (value ? Number(value) : null);

interface FeeEstimateFormProps {
  onResult: (response: AssistantFeeResponse) => void;
  onCancel: () => void;
}

/** The chat's fee form: asks the backend directly, with no AI. */
export default function FeeEstimateForm({ onResult, onCancel }: FeeEstimateFormProps) {
  const [values, setValues] = useState<Values>(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const field = (name: FieldName) => ({
    name,
    value: values[name],
    error: errors[name],
    onChange: (e: { target: { value: string } }) =>
      setValues((current) => ({ ...current, [name]: e.target.value })),
  });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validate(values);
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;

    const request: AssistantFeeRequest = {
      pickupDistrict: values.pickupDistrict,
      pickupCity: values.pickupCity.trim(),
      dropoffDistrict: values.dropoffDistrict,
      dropoffCity: values.dropoffCity.trim(),
      weightKg: Number(values.weightKg),
      lengthCm: optionalNumber(values.lengthCm),
      widthCm: optionalNumber(values.widthCm),
      heightCm: optionalNumber(values.heightCm),
    };
    setSubmitting(true);
    try {
      onResult(await assistantApi.estimateFee(request));
    } catch (err) {
      const error = toApiRequestError(err);
      setErrors(
        Object.fromEntries(error.fieldErrors.map((fe) => [fe.field, fe.message])) as Partial<
          Record<FieldName, string>
        >,
      );
      setFormError(error.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
      <h3 className="text-sm font-semibold text-slate-900">Estimate a delivery fee</h3>
      <div className="grid grid-cols-2 gap-3">
        <SelectField
          label="From district"
          options={DISTRICT_OPTIONS}
          {...field('pickupDistrict')}
        />
        <TextField label="From city" maxLength={60} {...field('pickupCity')} />
        <SelectField label="To district" options={DISTRICT_OPTIONS} {...field('dropoffDistrict')} />
        <TextField label="To city" maxLength={60} {...field('dropoffCity')} />
      </div>
      <TextField
        label="Weight (kg)"
        type="number"
        inputMode="decimal"
        min="0"
        step="0.1"
        {...field('weightKg')}
      />
      <fieldset>
        <legend className="mb-1.5 text-sm font-medium text-slate-700">
          Size in cm <span className="font-normal text-slate-400">(optional)</span>
        </legend>
        <div className="grid grid-cols-3 gap-2">
          <TextField label="Length" type="number" min="0" {...field('lengthCm')} />
          <TextField label="Width" type="number" min="0" {...field('widthCm')} />
          <TextField label="Height" type="number" min="0" {...field('heightCm')} />
        </div>
      </fieldset>
      {formError && (
        <p role="alert" className="text-xs font-medium text-red-600">
          {formError}
        </p>
      )}
      <div className="flex gap-2 pt-1">
        <Button type="button" variant="outline" size="sm" onClick={onCancel}>
          Back to chat
        </Button>
        <Button type="submit" size="sm" loading={submitting} className="flex-1">
          Get estimate
        </Button>
      </div>
    </form>
  );
}
