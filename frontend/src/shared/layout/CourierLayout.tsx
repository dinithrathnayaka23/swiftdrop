import { Suspense } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import Logo from '../../components/brand/Logo';
import ErrorBoundary from '../../components/feedback/ErrorBoundary';
import LoadingState from '../../components/feedback/LoadingState';
import NotificationBell from '../../features/shared-notification-bell/NotificationBell';
import { PATHS } from '../../routes/paths';
import { cn } from '../../utils/cn';
import { COURIER_TABS } from './navigation';
import UserMenu from './UserMenu';

/**
 * Shell for the courier app. Mobile first: a slim top bar, one centred column and a bottom tab
 * bar (Today's Deliveries, Scan Parcel, Completed, Account). Respects the phone's safe areas.
 */
export default function CourierLayout() {
  const { pathname } = useLocation();

  return (
    <div className="flex min-h-dvh flex-col bg-slate-50">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 pt-[env(safe-area-inset-top)] backdrop-blur">
        <div className="mx-auto flex h-16 max-w-2xl items-center gap-2 px-4">
          <Link to={PATHS.courierHome} aria-label="Today's deliveries">
            <Logo size="sm" />
          </Link>
          <div className="ml-auto flex items-center gap-1">
            <NotificationBell />
            <UserMenu compact />
          </div>
        </div>
      </header>

      {/* Bottom padding keeps content clear of the tab bar. */}
      <main className="mx-auto w-full max-w-2xl flex-1 px-4 pt-5 pb-[calc(env(safe-area-inset-bottom)+6rem)]">
        <ErrorBoundary variant="page" resetKey={pathname}>
          <Suspense fallback={<LoadingState />}>
            <Outlet />
          </Suspense>
        </ErrorBoundary>
      </main>

      <nav
        aria-label="Courier app"
        className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white pb-[env(safe-area-inset-bottom)]"
      >
        <ul className="mx-auto grid max-w-2xl grid-cols-4 gap-1 px-2 py-2">
          {COURIER_TABS.map(({ label, to, icon: Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                end
                className={({ isActive }) =>
                  cn(
                    'flex min-h-14 flex-col items-center justify-center gap-1 rounded-xl px-1 text-center text-[11px] leading-tight font-medium sm:text-xs',
                    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-500 hover:text-slate-900',
                  )
                }
              >
                <Icon className="size-5" aria-hidden="true" />
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
