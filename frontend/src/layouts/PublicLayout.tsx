import { Link, Outlet } from 'react-router-dom';
import Logo from '../components/Logo';
import { useAuth } from '../hooks/useAuth';
import { PATHS, ROLE_HOME } from '../routes/paths';

export default function PublicLayout() {
  const { user } = useAuth();

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link to={PATHS.home}>
            <Logo />
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <Link to={PATHS.track} className="text-slate-600 hover:text-slate-900">
              Track
            </Link>
            {user ? (
              <Link to={ROLE_HOME[user.role]} className="font-medium text-brand-600">
                My account
              </Link>
            ) : (
              <Link to={PATHS.login} className="font-medium text-brand-600">
                Sign in
              </Link>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
