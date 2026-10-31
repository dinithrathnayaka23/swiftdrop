import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError, getErrorMessage } from '../../api/errors';
import { useAuth } from '../../hooks/useAuth';
import { useForm } from '../../hooks/useForm';
import { PATHS, ROLE_HOME } from '../../routes/paths';
import type { LoginPortal } from '../../types/auth';
import { loadLoginPreferences, updateLoginPreferences } from '../../utils/loginPreferences';
import { validateLoginForm } from '../../utils/validation';
import Alert from '../ui/Alert';
import Button from '../ui/Button';
import Checkbox from '../ui/Checkbox';
import PasswordField from '../ui/PasswordField';
import TextField from '../ui/TextField';
import SignInPageLinks from './SignInPageLinks';

interface StaffSignInFormProps {
  portal: Exclude<LoginPortal, 'PUBLIC'>;
  /** Placeholder for the email field. */
  emailPlaceholder?: string;
  /** Off for the admin, whose password is set in the server configuration, not by email reset. */
  showForgotPassword?: boolean;
}

/**
 * Email + password sign-in for staff (couriers and admins). No Google and no sign-up: staff
 * accounts are created by an admin. The backend refuses accounts that belong on another page.
 */
export default function StaffSignInForm({
  portal,
  emailPlaceholder = 'you@swiftdrop.lk',
  showForgotPassword = true,
}: StaffSignInFormProps) {
  const { login } = useAuth();
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from;

  const [saved] = useState(loadLoginPreferences);
  const form = useForm({ email: saved?.email ?? '', password: '' }, validateLoginForm);
  const [remember, setRemember] = useState(saved?.rememberMe ?? false);
  const [formError, setFormError] = useState<string | null>(null);
  const [wrongPage, setWrongPage] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = form.handleSubmit(async ({ email, password }) => {
    setSubmitting(true);
    setFormError(null);
    setWrongPage(false);
    try {
      const response = await authApi.login({
        email: email.trim(),
        password,
        rememberMe: remember,
        portal,
      });
      updateLoginPreferences(response.rememberMe, email.trim());
      login({ token: response.token, user: response.user }, { remember: response.rememberMe });
      navigate(from ?? ROLE_HOME[response.user.role], { replace: true });
    } catch (err) {
      const apiError = err instanceof ApiRequestError ? err : null;
      setWrongPage(apiError?.code === 'WRONG_SIGN_IN_PAGE');
      // 401: wrong email/password. 403: the account is disabled, unconfirmed or on the wrong page.
      setFormError(apiError?.status === 401 ? 'Invalid email or password.' : getErrorMessage(err));
      setSubmitting(false);
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {formError && (
        <Alert tone="error">
          {formError}
          {wrongPage && <SignInPageLinks current={portal} />}
        </Alert>
      )}

      <TextField
        label="Email"
        type="email"
        autoComplete="username"
        inputMode="email"
        placeholder={emailPlaceholder}
        maxLength={255}
        {...form.field('email')}
      />
      <PasswordField
        label="Password"
        autoComplete="current-password"
        placeholder="Enter your password"
        autoFocus={Boolean(saved?.email)}
        {...form.field('password')}
      />

      <div className="flex items-center justify-between gap-3">
        <Checkbox
          label="Remember me"
          title="Stay signed in on this device for 30 days"
          checked={remember}
          onChange={(e) => setRemember(e.target.checked)}
        />
        {showForgotPassword && (
          <Link
            to={PATHS.forgotPassword}
            className="py-2 text-sm font-medium text-brand-600 hover:text-brand-700"
          >
            Forgot Password?
          </Link>
        )}
      </div>

      <Button type="submit" size="lg" fullWidth loading={submitting}>
        {submitting ? 'Signing In...' : 'Sign In'}
      </Button>
    </form>
  );
}
