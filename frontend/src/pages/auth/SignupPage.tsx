import { Link } from 'react-router-dom';
import PagePlaceholder from '../../components/PagePlaceholder';
import { PATHS } from '../../routes/paths';

export default function SignupPage() {
  return (
    <div className="mx-auto max-w-md">
      <PagePlaceholder
        title="Create an account"
        description="Sign up as a customer, or apply as a merchant (requires admin approval)."
      />
      <p className="mt-4 text-sm text-slate-600">
        Already registered?{' '}
        <Link to={PATHS.login} className="text-brand-600">
          Sign in
        </Link>
      </p>
    </div>
  );
}
