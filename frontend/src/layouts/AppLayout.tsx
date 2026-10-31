import { LogOut } from 'lucide-react';
import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import Logo from '../components/brand/Logo';
import SideNav from '../components/SideNav';
import Button from '../components/ui/Button';
import { useAuth } from '../hooks/useAuth';
import { PATHS, ROLE_SIGN_IN } from '../routes/paths';
import { formatRole } from '../utils/format';

/**
 * Shell for signed-in users: top bar + role-aware sidebar.
 * Mobile: sidebar is a slide-over opened from the menu button. Desktop (md+): always visible.
 */
export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const [lastPath, setLastPath] = useState(location.pathname);

  // Close the mobile menu after navigating.
  if (location.pathname !== lastPath) {
    setLastPath(location.pathname);
    setMenuOpen(false);
  }

  return (
    <div className="min-h-screen md:flex">
      {/* Sidebar */}
      <aside
        className={`fixed inset-y-0 left-0 z-30 w-64 transform border-r border-slate-200 bg-white transition-transform md:static md:translate-x-0 ${
          menuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
        aria-label="Main navigation"
      >
        <div className="flex h-16 items-center border-b border-slate-200 px-4">
          <Link to={PATHS.home} aria-label="SwiftDrop home">
            <Logo />
          </Link>
        </div>
        {user && <SideNav role={user.role} />}
      </aside>

      {menuOpen && (
        <button
          type="button"
          aria-label="Close menu"
          className="fixed inset-0 z-20 bg-slate-900/40 md:hidden"
          onClick={() => setMenuOpen(false)}
        />
      )}

      {/* Main column */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-4">
          <button
            type="button"
            className="rounded p-2 text-slate-600 hover:bg-slate-100 md:hidden"
            aria-label="Open menu"
            onClick={() => setMenuOpen(true)}
          >
            <svg
              viewBox="0 0 24 24"
              className="h-5 w-5"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path d="M4 6h16M4 12h16M4 18h16" strokeLinecap="round" />
            </svg>
          </button>
          <div className="ml-auto flex items-center gap-3 text-sm">
            {user && (
              <span className="hidden text-slate-600 sm:inline">
                {user.displayName} · {formatRole(user.role)}
              </span>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                void logout();
                // Admins go back to the admin sign-in page; customers and merchants to the site.
                navigate(user?.role === 'DISPATCHER_ADMIN' ? ROLE_SIGN_IN[user.role] : PATHS.home);
              }}
            >
              <LogOut className="size-4" aria-hidden="true" />
              Sign out
            </Button>
          </div>
        </header>
        <main className="flex-1 p-4 md:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
