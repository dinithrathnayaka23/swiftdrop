import { Link } from 'react-router';
import AuthCard from '../../components/auth/AuthCard';
import StaffSignInForm from '../../components/auth/StaffSignInForm';
import { PATHS } from '../../routes/paths';

/** Sign-in for the one SwiftDrop admin, whose credentials come from ADMIN_EMAIL / ADMIN_PASSWORD. */
export default function AdminLoginPage() {
  return (
    <AuthCard
      backTo={PATHS.home}
      title="Admin Portal"
      subtitle="Sign in to manage dispatch, couriers and merchants"
      footer={
        <>
          Not a SwiftDrop admin?{' '}
          <Link to={PATHS.login} className="font-semibold text-brand-600 hover:text-brand-700">
            Go to the main sign-in
          </Link>
        </>
      }
    >
      <StaffSignInForm portal="ADMIN" showForgotPassword={false} />
      <p className="mt-5 text-center text-sm text-slate-500 lg:text-left">
        The admin email and password are set in the server configuration (.env).
      </p>
    </AuthCard>
  );
}
