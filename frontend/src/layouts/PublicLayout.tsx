import { LayoutDashboard, Menu, X } from 'lucide-react';
import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import Logo from '../components/brand/Logo';
import SiteFooter from '../components/layout/SiteFooter';
import ButtonLink from '../components/ui/ButtonLink';
import { useAuth } from '../hooks/useAuth';
import { PATHS, ROLE_HOME } from '../routes/paths';
import { cn } from '../utils/cn';

const NAV_LINKS = [
  { label: 'Track Parcel', to: PATHS.track },
  { label: 'Services', to: '/#services' },
  { label: 'For Businesses', to: PATHS.merchantSignup },
  { label: 'Help', to: '/#help' },
];

/** Header + footer for public pages (landing, tracking, error pages). */
export default function PublicLayout() {
  const { user } = useAuth();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [lastPath, setLastPath] = useState(location.pathname + location.hash);

  // Close the mobile menu after navigating.
  if (location.pathname + location.hash !== lastPath) {
    setLastPath(location.pathname + location.hash);
    setMenuOpen(false);
  }

  const accountActions = user ? (
    <ButtonLink to={ROLE_HOME[user.role]} size="sm">
      <LayoutDashboard className="size-4" aria-hidden="true" />
      Dashboard
    </ButtonLink>
  ) : (
    <>
      <ButtonLink to={PATHS.login} variant="outline" size="sm">
        Sign In
      </ButtonLink>
      <ButtonLink to={PATHS.signup} size="sm">
        Create Account
      </ButtonLink>
    </>
  );

  return (
    <div className="flex min-h-screen flex-col bg-white">
      <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-6 px-4 sm:px-6 lg:px-8">
          <Link to={PATHS.home} aria-label="SwiftDrop home" className="shrink-0">
            <Logo />
          </Link>

          <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
            {NAV_LINKS.map((link) => (
              <NavLink
                key={link.label}
                to={link.to}
                className={({ isActive }) =>
                  cn(
                    'rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    isActive && !link.to.includes('#')
                      ? 'text-brand-700'
                      : 'text-slate-600 hover:text-slate-900',
                  )
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto hidden items-center gap-3 md:flex">{accountActions}</div>

          <button
            type="button"
            className="ml-auto rounded-md p-2 text-slate-600 hover:bg-slate-100 md:hidden"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={menuOpen}
            aria-controls="mobile-menu"
            onClick={() => setMenuOpen((open) => !open)}
          >
            {menuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>

        {menuOpen && (
          <div
            id="mobile-menu"
            className="border-t border-slate-200 bg-white px-4 pt-2 pb-4 md:hidden"
          >
            <nav aria-label="Mobile" className="flex flex-col">
              {NAV_LINKS.map((link) => (
                <Link
                  key={link.label}
                  to={link.to}
                  className="rounded-md px-3 py-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  {link.label}
                </Link>
              ))}
            </nav>
            <div className="mt-3 grid grid-cols-2 gap-3 [&>*:only-child]:col-span-2">
              {accountActions}
            </div>
          </div>
        )}
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <SiteFooter />
    </div>
  );
}
