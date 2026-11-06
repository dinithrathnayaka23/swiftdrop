import { ArrowLeft, ArrowRight, Package } from 'lucide-react';
import Button from '../../../components/ui/Button';
import SelectField from '../../../components/ui/SelectField';
import TextAreaField from '../../../components/ui/TextAreaField';
import TextField from '../../../components/ui/TextField';
import type { ParcelForm, ParcelLimits, ParcelValues } from '../bookingForm';
import { PARCEL_TYPES } from '../../../types/settings';

const TYPE_LABELS: Record<(typeof PARCEL_TYPES)[number], string> = {
  DOCUMENT: 'Document',
  PARCEL: 'Parcel',
  FRAGILE: 'Fragile',
  ELECTRONICS: 'Electronics',
  PERISHABLE: 'Perishable',
};

const TYPE_OPTIONS = PARCEL_TYPES.map((type) => ({ value: type, label: TYPE_LABELS[type] }));

interface ParcelStepProps {
  form: ParcelForm;
  limits: ParcelLimits;
  onBack: () => void;
  onContinue: (values: ParcelValues) => void;
}

export default function ParcelStep({ form, limits, onBack, onContinue }: ParcelStepProps) {
  return (
    <form noValidate onSubmit={form.handleSubmit(onContinue)} className="space-y-6">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900">
          <Package className="size-5 text-brand-500" aria-hidden="true" />
          Parcel Details
        </h2>
        <p className="text-sm text-slate-500">
          Tell us what you are sending. Maximum weight {limits.maxWeightKg} kg, and each side up to{' '}
          {limits.maxSideCm} cm.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Parcel type" options={TYPE_OPTIONS} {...form.field('parcelType')} />
        <TextField
          label="Weight (kg)"
          inputMode="decimal"
          placeholder="e.g. 2.5"
          {...form.field('weightKg')}
        />
      </div>

      <div>
        <p className="mb-2 text-sm font-medium text-slate-700">
          Size (optional, enter all three sides or none)
        </p>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Length (cm)" inputMode="decimal" optional {...form.field('lengthCm')} />
          <TextField label="Width (cm)" inputMode="decimal" optional {...form.field('widthCm')} />
          <TextField label="Height (cm)" inputMode="decimal" optional {...form.field('heightCm')} />
        </div>
      </div>

      <TextAreaField
        label="Contents"
        optional
        rows={2}
        placeholder="For example: books, clothes, phone accessories"
        {...form.field('contentDescription')}
      />
      <TextAreaField
        label="Delivery instructions"
        optional
        rows={2}
        placeholder="For example: call on arrival, leave with the guard"
        {...form.field('deliveryInstructions')}
      />

      <div className="flex items-center justify-between gap-3">
        <Button variant="outline" onClick={onBack}>
          <ArrowLeft className="size-4" aria-hidden="true" />
          Back
        </Button>
        <Button type="submit">
          Review & Estimate
          <ArrowRight className="size-4" aria-hidden="true" />
        </Button>
      </div>
    </form>
  );
}
