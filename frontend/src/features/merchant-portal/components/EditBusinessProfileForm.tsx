import { useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import PhoneField from '../../../components/ui/PhoneField';
import TextAreaField from '../../../components/ui/TextAreaField';
import TextField from '../../../components/ui/TextField';
import { useForm } from '../../../hooks/useForm';
import { toE164 } from '../../../utils/phone';
import { validateAddress, validateEmail, validatePhone } from '../../../utils/validation';
import { splitPhone } from '../../profile/phoneParts';
import { merchantPortalApi } from '../api';
import type { MerchantProfile } from '../types';

interface EditBusinessProfileFormProps {
  profile: MerchantProfile;
  onSaved: (profile: MerchantProfile) => void;
  onCancel: () => void;
}

/** Edits the business contact details, with the same rules as merchant sign-up. */
export default function EditBusinessProfileForm({
  profile,
  onSaved,
  onCancel,
}: EditBusinessProfileFormProps) {
  const phone = splitPhone(profile.contactPhone);
  const form = useForm(
    {
      contactEmail: profile.contactEmail,
      countryCode: phone.countryCode,
      contactPhone: phone.number,
      businessAddress: profile.businessAddress,
    },
    (v) => ({
      contactEmail: validateEmail(v.contactEmail),
      contactPhone: validatePhone(v.countryCode, v.contactPhone),
      businessAddress: validateAddress(v.businessAddress),
    }),
  );
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const onSubmit = form.handleSubmit(async (values) => {
    setSaving(true);
    setFormError(null);
    try {
      const saved = await merchantPortalApi.updateProfile({
        contactEmail: values.contactEmail.trim(),
        contactPhone: toE164(values.countryCode, values.contactPhone),
        businessAddress: values.businessAddress.trim(),
      });
      onSaved(saved);
    } catch (err) {
      // Field problems go on the input; anything else above the form.
      if (!form.applyServerErrors(err)) setFormError(getErrorMessage(err));
      setSaving(false);
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {formError && <Alert tone="error">{formError}</Alert>}
      <TextField
        label="Contact Email"
        type="email"
        autoComplete="email"
        maxLength={255}
        {...form.field('contactEmail')}
      />
      <PhoneField
        label="Contact Phone"
        name="contactPhone"
        countryCode={form.values.countryCode}
        onCountryCodeChange={(code) => form.setValue('countryCode', code)}
        number={form.values.contactPhone}
        onNumberChange={(number) => form.setValue('contactPhone', number)}
        onBlur={() => form.touch('contactPhone')}
        error={form.errors.contactPhone}
      />
      <TextAreaField
        label="Business Address"
        rows={3}
        maxLength={500}
        {...form.field('businessAddress')}
      />
      <div className="flex gap-3">
        <Button type="submit" loading={saving}>
          Save Changes
        </Button>
        <Button variant="outline" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
