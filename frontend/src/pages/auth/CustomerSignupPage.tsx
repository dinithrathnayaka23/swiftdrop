import { Store } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { authApi } from '../../api/auth';
import { getErrorMessage } from '../../api/errors';
import AuthCard from '../../components/auth/AuthCard';
import GoogleSignInButton from '../../components/auth/GoogleSignInButton';
import Alert from '../../components/ui/Alert';
import Button from '../../components/ui/Button';
import PasswordField from '../../components/ui/PasswordField';
import PhoneField from '../../components/ui/PhoneField';
import TextField from '../../components/ui/TextField';
import type { CheckEmailState } from './CheckEmailPage';
import { useForm } from '../../hooks/useForm';
import { useGoogleSignIn } from '../../hooks/useGoogleSignIn';
import { PATHS } from '../../routes/paths';
import { DEFAULT_COUNTRY_CODE, toE164 } from '../../utils/phone';
import { validateCustomerSignupForm } from '../../utils/validation';

/** Self-registration for customers. Couriers and dispatchers are created by an admin. */
export default function CustomerSignupPage() {
  const navigate = useNavigate();

  const form = useForm(
    {
      fullName: '',
      email: '',
      countryCode: DEFAULT_COUNTRY_CODE as string,
      phone: '',
      password: '',
    },
    validateCustomerSignupForm,
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Signing up with Google creates a customer account (or signs in to an existing one).
  const google = useGoogleSignIn({ rememberMe: false, onError: setFormError });

  const onSubmit = form.handleSubmit(async (values) => {
    setSubmitting(true);
    setFormError(null);
    try {
      const response = await authApi.registerCustomer({
        fullName: values.fullName.trim(),
        email: values.email.trim(),
        phone: toE164(values.countryCode, values.phone),
        password: values.password,
      });
      // No session yet: the email address has to be confirmed first.
      const state: CheckEmailState = { email: response.email };
      navigate(PATHS.checkEmail, { replace: true, state });
    } catch (err) {
      // Field problems (e.g. email or registration number already used) go on the input;
      // anything else is shown above the form.
      if (!form.applyServerErrors(err)) setFormError(getErrorMessage(err));
      setSubmitting(false);
    }
  });

  return (
    <AuthCard
      backTo={PATHS.home}
      title="Create Customer Account"
      subtitle="Sign up to track parcels, manage deliveries and more."
      footer={
        <>
          Already have an account?{' '}
          <Link to={PATHS.login} className="font-semibold text-brand-600 hover:text-brand-700">
            Sign In
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {formError && <Alert tone="error">{formError}</Alert>}

        <TextField
          label="Full Name"
          autoComplete="name"
          placeholder="Enter your full name"
          maxLength={120}
          {...form.field('fullName')}
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          maxLength={255}
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
        <PasswordField
          label="Password"
          autoComplete="new-password"
          placeholder="Create a password"
          showStrength
          {...form.field('password')}
        />

        <Button
          type="submit"
          fullWidth
          loading={submitting}
          disabled={google.busy}
          className="mt-2"
        >
          {submitting ? 'Creating Account...' : 'Create Account'}
        </Button>
      </form>

      <GoogleSignInButton text="signup_with" onCredential={google.signIn} busy={google.busy} />

      <Link
        to={PATHS.merchantSignup}
        className="mt-6 flex items-center gap-3 rounded-xl border border-slate-200 p-3.5 text-left transition hover:border-brand-200 hover:bg-brand-50/50"
      >
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
          <Store className="size-4.5" aria-hidden="true" />
        </span>
        <span className="text-sm">
          <span className="block font-semibold text-slate-900">Registering a business?</span>
          <span className="text-slate-500">Create a merchant account instead</span>
        </span>
      </Link>
    </AuthCard>
  );
}
