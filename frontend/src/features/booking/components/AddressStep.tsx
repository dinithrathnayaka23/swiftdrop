import { ArrowRight, MapPin } from 'lucide-react';
import Button from '../../../components/ui/Button';
import PhoneField from '../../../components/ui/PhoneField';
import SelectField from '../../../components/ui/SelectField';
import TextField from '../../../components/ui/TextField';
import {
  addressKey,
  type AddressForm,
  type AddressPrefix,
  type AddressValues,
} from '../bookingForm';
import { DISTRICTS } from '../districts';

const DISTRICT_OPTIONS = [
  { value: '', label: 'Select district' },
  ...DISTRICTS.map((district) => ({ value: district, label: district })),
];

interface AddressSectionProps {
  form: AddressForm;
  prefix: AddressPrefix;
  title: string;
  nameLabel: string;
}

function AddressSection({ form, prefix, title, nameLabel }: AddressSectionProps) {
  const key = (suffix: Parameters<typeof addressKey>[1]) => addressKey(prefix, suffix);

  return (
    <section aria-labelledby={`${prefix}-title`}>
      <h3
        id={`${prefix}-title`}
        className="mb-4 flex items-center gap-2 text-base font-semibold text-slate-900"
      >
        <MapPin className="size-4 text-brand-500" aria-hidden="true" />
        {title}
      </h3>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label={nameLabel} autoComplete="off" {...form.field(key('Name'))} />
        <PhoneField
          label="Phone number"
          name={key('Phone')}
          countryCode={form.values[key('CountryCode')]}
          number={form.values[key('Phone')]}
          onCountryCodeChange={(code) => form.setValue(key('CountryCode'), code)}
          onNumberChange={(number) => form.setValue(key('Phone'), number)}
          onBlur={() => form.touch(key('Phone'))}
          error={form.errors[key('Phone')]}
        />
        <TextField label="Address line 1" {...form.field(key('Line1'))} />
        <TextField
          label="Address line 2"
          optional
          placeholder="Apartment, suite, etc."
          {...form.field(key('Line2'))}
        />
        <TextField label="City" {...form.field(key('City'))} />
        <SelectField label="District" options={DISTRICT_OPTIONS} {...form.field(key('District'))} />
        <TextField label="Postal code" optional {...form.field(key('PostalCode'))} />
      </div>
    </section>
  );
}

interface AddressStepProps {
  form: AddressForm;
  onContinue: (values: AddressValues) => void;
  onCancel: () => void;
}

export default function AddressStep({ form, onContinue, onCancel }: AddressStepProps) {
  return (
    <form noValidate onSubmit={form.handleSubmit(onContinue)} className="space-y-8">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">Pickup & Drop-off Details</h2>
        <p className="text-sm text-slate-500">
          Enter the pickup and delivery addresses with contact information.
        </p>
      </div>

      <AddressSection form={form} prefix="pickup" title="Pickup Details" nameLabel="Contact name" />
      <hr className="border-slate-200" />
      <AddressSection
        form={form}
        prefix="dropoff"
        title="Drop-off Details"
        nameLabel="Recipient name"
      />

      <div className="flex items-center justify-between gap-3">
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit">
          Continue to Parcel Details
          <ArrowRight className="size-4" aria-hidden="true" />
        </Button>
      </div>
    </form>
  );
}
