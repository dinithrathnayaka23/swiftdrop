import { KeyRound, LogOut } from 'lucide-react';
import { useNavigate } from 'react-router';
import Button from '../../../components/ui/Button';
import ButtonLink from '../../../components/ui/ButtonLink';
import { useAuth } from '../../../hooks/useAuth';
import { PATHS, ROLE_SIGN_IN } from '../../../routes/paths';
import { formatRole, initials } from '../../../utils/format';

/** Courier app "Account" tab: who is signed in, password reset and sign out. */
export default function AccountPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!user) return null;

  return (
    <section>
      <h1 className="text-2xl font-bold tracking-tight text-slate-900">Account</h1>

      <div className="mt-5 flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-5">
        <span
          aria-hidden="true"
          className="flex size-14 shrink-0 items-center justify-center rounded-full bg-brand-50 text-lg font-semibold text-brand-700"
        >
          {initials(user.displayName)}
        </span>
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold text-slate-900">{user.displayName}</p>
          <p className="text-sm text-slate-500">{formatRole(user.role)}</p>
        </div>
      </div>

      <div className="mt-4 grid gap-3">
        <ButtonLink to={PATHS.forgotPassword} variant="outline" size="lg" fullWidth>
          <KeyRound className="size-5" aria-hidden="true" />
          Change Password
        </ButtonLink>
        <Button
          variant="danger"
          size="lg"
          fullWidth
          onClick={() => {
            void logout();
            navigate(ROLE_SIGN_IN[user.role], { replace: true });
          }}
        >
          <LogOut className="size-5" aria-hidden="true" />
          Sign Out
        </Button>
      </div>
    </section>
  );
}
