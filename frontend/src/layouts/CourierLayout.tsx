import { LogOut } from 'lucide-react';
import { Link, Outlet, useNavigate } from 'react-router';
import Logo from '../components/brand/Logo';
import { useAuth } from '../hooks/useAuth';
import { PATHS } from '../routes/paths';

/**
 * Shell for the courier app. Mobile first: a slim sticky top bar and one centred column, no
 * sidebar. Respects the phone's safe areas (notch, home indicator).
 */
export default function CourierLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="flex min-h-dvh flex-col bg-slate-50">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 pt-[env(safe-area-inset-top)] backdrop-blur">
        <div className="mx-auto flex h-14 max-w-2xl items-center gap-3 px-4">
          <Link to={PATHS.courierHome} aria-label="My jobs">
            <Logo size="sm" />
          </Link>
          {user && (
            <span className="ml-auto truncate text-sm font-medium text-slate-700">
              {user.displayName}
            </span>
          )}
          <button
            type="button"
            aria-label="Sign out"
            title="Sign out"
            className="-mr-2 inline-flex size-11 shrink-0 items-center justify-center rounded-full text-slate-600 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-brand-600"
            onClick={() => {
              void logout();
              navigate(PATHS.courierLogin, { replace: true });
            }}
          >
            <LogOut className="size-5" aria-hidden="true" />
          </button>
        </div>
      </header>
      <main className="mx-auto w-full max-w-2xl flex-1 px-4 pt-5 pb-[max(env(safe-area-inset-bottom),1.5rem)]">
        <Outlet />
      </main>
    </div>
  );
}
