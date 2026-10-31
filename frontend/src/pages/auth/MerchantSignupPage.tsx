import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { authApi } from '../../api/auth';
import { getErrorMessage } from '../../api/errors';
import AuthCard from '../../components/auth/AuthCard';
import Alert from '../../components/ui/Alert';
import Button from '../../components/ui/Button';
import PasswordField from '../../components/ui/PasswordField';
import PhoneField from '../../components/ui/PhoneField';
import TextAreaField from '../../components/ui/TextAreaField';
import TextField from '../../components/ui/TextField';
import { useForm } from '../../hooks/useForm';
import { PATHS } from '../../routes/paths';
import type { MerchantRegistrationResponse } from '../../types/auth';
import { DEFAULT_COUNTRY_CODE, toE164 } from '../../utils/phone';
import { normalizeRegistrationNo, validateMerchantSignupForm } from '../../utils/validation';

/** Navigation state passed to the "Registration Submitted" page. */
export type MerchantSubmittedState = Pick<
  MerchantRegistrationResponse,
  'businessName' | 'registrationNo' | 'submittedAt'
>;

/**
 * Merchant registration: creates the contact person's MERCHANT account and a PENDING merchant.
 * The merchant can sign in once an admin approves the business.
 */
export default function MerchantSignupPage() {
  const navigate = useNavigate();

  const form = useForm(
    {
      businessName: '',
      registrationNo: '',
      contactPersonName: '',
      email: '',
      countryCode: DEFAULT_COUNTRY_CODE as string,
      phone: '',
      businessAddress: '',
      password: '',
    },
    validateMerchantSignupForm,
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = form.handleSubmit(async (values) => {
    setSubmitting(true);
    setFormError(null);
    try {
      const response = await authApi.registerMerchant({
        businessName: values.businessName.trim(),
        registrationNo: normalizeRegistrationNo(values.registrationNo),
        contactPersonName: values.contactPersonName.trim(),
        email: values.email.trim(),
        phone: toE164(values.countryCode, values.phone),
        businessAddress: values.businessAddress.trim(),
        password: values.password,
      });
      const state: MerchantSubmittedState = {
        businessName: response.businessName,
        registrationNo: response.registrationNo,
        submittedAt: response.submittedAt,
      };
      navigate(PATHS.merchantSubmitted, { replace: true, state });
    } catch (err) {
      // Field problems (e.g. email or registration number already used) go on the input;
      // anything else is shown above the form.
      if (!form.applyServerErrors(err)) setFormError(getErrorMessage(err));
      setSubmitting(false);
    }
  });

  return (
    <AuthCard
      backTo={PATHS.signup}
      title="Create Merchant Account"
      subtitle="Register your business to start shipping with SwiftDrop."
      footer={
        <>
          Already registered?{' '}
          <Link to={PATHS.login} className="font-semibold text-brand-600 hover:text-brand-700">
            Sign In
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {formError && <Alert tone="error">{formError}</Alert>}

        <TextField
          label="Business Name"
          autoComplete="organization"
          placeholder="Enter business name"
          maxLength={180}
          {...form.field('businessName')}
        />
        <TextField
          label="Business Registration No."
          placeholder="e.g. PV00234567"
          hint="PV, PB or GA followed by 8 digits, as on your certificate of incorporation."
          maxLength={14}
          autoCapitalize="characters"
          spellCheck={false}
          className="uppercase placeholder:normal-case"
          {...form.field('registrationNo')}
        />
        <TextField
          label="Contact Person Name"
          autoComplete="name"
          placeholder="Enter contact person name"
          maxLength={120}
          {...form.field('contactPersonName')}
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          maxLength={255}
          hint="Used to sign in and for approval updates."
          {...form.field('email')}
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
        <TextAreaField
          label="Business Address"
          autoComplete="street-address"
          placeholder="Enter business address"
          maxLength={500}
          {...form.field('businessAddress')}
        />
        <PasswordField
          label="Password"
          autoComplete="new-password"
          placeholder="Create a password"
          showStrength
          {...form.field('password')}
        />

        <Button type="submit" fullWidth loading={submitting} className="mt-2">
          {submitting ? 'Submitting...' : 'Submit for Approval'}
        </Button>
      </form>
    </AuthCard>
  );
}
