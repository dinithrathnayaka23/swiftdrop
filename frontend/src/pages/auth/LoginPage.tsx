import { useCallback, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { authApi } from '../../api/auth';
import { ApiRequestError, getErrorMessage } from '../../api/errors';
import AuthCard from '../../components/auth/AuthCard';
import GoogleSignInButton from '../../components/auth/GoogleSignInButton';
import ResendVerificationButton from '../../components/auth/ResendVerificationButton';
import SignInPageLinks from '../../components/auth/SignInPageLinks';
import Alert from '../../components/ui/Alert';
import Button from '../../components/ui/Button';
import Checkbox from '../../components/ui/Checkbox';
import PasswordField from '../../components/ui/PasswordField';
import TextField from '../../components/ui/TextField';
import { useAuth } from '../../hooks/useAuth';
import { useForm } from '../../hooks/useForm';
import { useGoogleSignIn } from '../../hooks/useGoogleSignIn';
import { PATHS, ROLE_HOME } from '../../routes/paths';
import { loadLoginPreferences, updateLoginPreferences } from '../../utils/loginPreferences';
import { validateLoginForm } from '../../utils/validation';

/** One sign-in page for every role; users land on their role's home afterwards. */
export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  // If "Remember me" was ticked last time on this device, start ticked with the email filled in.
  // Otherwise unticked: safer on shared computers.
  const [saved] = useState(loadLoginPreferences);
  const form = useForm({ email: saved?.email ?? '', password: '' }, validateLoginForm);
  const [remember, setRemember] = useState(saved?.rememberMe ?? false);
  const [formError, setFormError] = useState<string | null>(null);
  /** Set when the password was right but the email isn't confirmed yet. */
  const [unconfirmedEmail, setUnconfirmedEmail] = useState<string | null>(null);
  /** Set when a courier or admin used this page; they have their own sign-in pages. */
  const [wrongPage, setWrongPage] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const onGoogleError = useCallback((message: string | null, code?: string) => {
    setWrongPage(code === 'WRONG_SIGN_IN_PAGE');
    setFormError(message);
  }, []);

  const google = useGoogleSignIn({
    rememberMe: remember,
    redirectTo: from,
    // Keep "Remember me" (and the saved email) as chosen; Google doesn't need the email field.
    onSuccess: (response) =>
      updateLoginPreferences(response.rememberMe, saved?.email ?? form.values.email.trim()),
    onError: onGoogleError,
  });

  const onSubmit = form.handleSubmit(async ({ email, password }) => {
    setSubmitting(true);
    setFormError(null);
    setUnconfirmedEmail(null);
    setWrongPage(false);
    try {
      const response = await authApi.login({ email: email.trim(), password, rememberMe: remember });
      updateLoginPreferences(response.rememberMe, email.trim());
      login({ token: response.token, user: response.user }, { remember: response.rememberMe });
      navigate(from ?? ROLE_HOME[response.user.role], { replace: true });
    } catch (err) {
      if (err instanceof ApiRequestError && err.code === 'EMAIL_NOT_VERIFIED') {
        setUnconfirmedEmail(email.trim());
        setSubmitting(false);
        return;
      }
      setWrongPage(err instanceof ApiRequestError && err.code === 'WRONG_SIGN_IN_PAGE');
      // 401: wrong email/password. 403: correct password but the account is pending approval or
      // disabled; the backend's message explains which.
      setFormError(
        err instanceof ApiRequestError && err.status === 401
          ? 'Invalid email or password.'
          : getErrorMessage(err),
      );
      setSubmitting(false);
    }
  });

  return (
    <AuthCard
      backTo={PATHS.home}
      title="Welcome Back"
      subtitle="Sign in to your account to continue"
      footer={
        <>
          Don&apos;t have an account?{' '}
          <Link to={PATHS.signup} className="font-semibold text-brand-600 hover:text-brand-700">
            Create Account
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        {formError && (
          <Alert tone="error">
            {formError}
            {wrongPage && <SignInPageLinks current="PUBLIC" />}
          </Alert>
        )}
        {unconfirmedEmail && (
          <Alert tone="warning" title="Confirm your email first">
            <p>
              We sent a confirmation link to <strong>{unconfirmedEmail}</strong> when you signed up.
              Open it, then sign in again.
            </p>
            <div className="mt-3">
              <ResendVerificationButton email={unconfirmedEmail} />
            </div>
          </Alert>
        )}
        {google.busy && <Alert tone="info">Signing you in with Google...</Alert>}

        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          maxLength={255}
          {...form.field('email')}
        />
        <PasswordField
          label="Password"
          autoComplete="current-password"
          placeholder="Enter your password"
          // Email already filled in from last time: go straight to the password.
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
          <Link
            to={PATHS.forgotPassword}
            className="text-sm font-medium text-brand-600 hover:text-brand-700"
          >
            Forgot Password?
          </Link>
        </div>

        <Button type="submit" fullWidth loading={submitting} disabled={google.busy}>
          {submitting ? 'Signing In...' : 'Sign In'}
        </Button>
      </form>

      <GoogleSignInButton text="signin_with" onCredential={google.signIn} busy={google.busy} />
    </AuthCard>
  );
}
