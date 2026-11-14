import { useState } from 'react';
import { getErrorMessage } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import PhoneField from '../../../components/ui/PhoneField';
import TextField from '../../../components/ui/TextField';
import { useForm } from '../../../hooks/useForm';
import { toE164 } from '../../../utils/phone';
import { validatePersonName, validatePhone } from '../../../utils/validation';
import { profileApi } from '../api';
import { splitPhone } from '../phoneParts';
import type { UserProfile } from '../types';

interface EditProfileFormProps {
  profile: UserProfile;
  onSaved: (profile: UserProfile) => void;
  onCancel: () => void;
}

/** Edits the user's name and phone number, with the same rules as sign-up. */
export default function EditProfileForm({ profile, onSaved, onCancel }: EditProfileFormProps) {
  const phone = splitPhone(profile.phone);
  const form = useForm(
    { fullName: profile.fullName, countryCode: phone.countryCode, phone: phone.number },
    (v) => ({
      fullName: validatePersonName(v.fullName),
      phone: validatePhone(v.countryCode, v.phone),
    }),
  );
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const onSubmit = form.handleSubmit(async (values) => {
    setSaving(true);
    setFormError(null);
    try {
      const saved = await profileApi.update({
        fullName: values.fullName.trim(),
        phone: toE164(values.countryCode, values.phone),
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
        label="Full Name"
        autoComplete="name"
        maxLength={120}
        {...form.field('fullName')}
      />
      <PhoneField
        label="Phone Number"
        name="phone"
        countryCode={form.values.countryCode}
        onCountryCodeChange={(code) => form.setValue('countryCode', code)}
        number={form.values.phone}
        onNumberChange={(number) => form.setValue('phone', number)}
        onBlur={() => form.touch('phone')}
        error={form.errors.phone}
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
