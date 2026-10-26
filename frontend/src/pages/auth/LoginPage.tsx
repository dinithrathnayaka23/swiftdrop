import { Link, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { PATHS, ROLE_HOME } from '../../routes/paths';
import { ROLES, type Role } from '../../types/auth';
import { isDevLoginEnabled } from '../../utils/env';
import { formatRole } from '../../utils/format';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  // TEMPORARY: lets the team click through role-guarded screens before the identity
  // module exists. The fake token is rejected by the backend. Remove once real login works.
  function devLogin(role: Role) {
    login({
      token: 'dev-placeholder-token',
      user: { id: `dev-${role.toLowerCase()}`, displayName: `Dev ${formatRole(role)}`, role },
    });
    navigate(from ?? ROLE_HOME[role], { replace: true });
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="text-2xl font-semibold">Sign in</h1>

      {/* Placeholder form: wired to POST /api/v1/auth/login by the identity module. */}
      <form className="mt-6 space-y-4" onSubmit={(e) => e.preventDefault()}>
        <label className="block text-sm">
          <span className="text-slate-700">Email</span>
          <input
            type="email"
            autoComplete="email"
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
          />
        </label>
        <label className="block text-sm">
          <span className="text-slate-700">Password</span>
          <input
            type="password"
            autoComplete="current-password"
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
          />
        </label>
        <button
          type="submit"
          disabled
          className="w-full rounded bg-brand-600 px-4 py-2 text-white disabled:opacity-50"
        >
          Sign in (not implemented)
        </button>
        <button
          type="button"
          disabled
          className="w-full rounded border border-slate-300 px-4 py-2 text-slate-700 disabled:opacity-50"
        >
          Continue with Google (not implemented)
        </button>
      </form>

      <p className="mt-4 text-sm text-slate-600">
        No account?{' '}
        <Link to={PATHS.signup} className="text-brand-600">
          Sign up
        </Link>
      </p>

      {isDevLoginEnabled() && (
        <div className="mt-8 rounded border border-amber-300 bg-amber-50 p-4">
          <p className="text-sm font-medium text-amber-900">Dev only: sign in as a role</p>
          <div className="mt-3 grid grid-cols-2 gap-2">
            {ROLES.map((role) => (
              <button
                key={role}
                type="button"
                onClick={() => devLogin(role)}
                className="rounded border border-amber-300 bg-white px-2 py-1.5 text-xs text-amber-900 hover:bg-amber-100"
              >
                {formatRole(role)}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
