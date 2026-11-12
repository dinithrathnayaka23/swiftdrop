import { Menu, X } from 'lucide-react';
import { lazy, Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router';
import ErrorBoundary from '../../components/feedback/ErrorBoundary';
import LoadingState from '../../components/feedback/LoadingState';
import NotificationBell from '../../features/shared-notification-bell/NotificationBell';
import { useAuth } from '../../hooks/useAuth';
import { ROLE_HOME } from '../../routes/paths';
import { SIDEBAR } from './navigation';
import Sidebar from './Sidebar';
import TrackingSearch from './TrackingSearch';
import UserMenu from './UserMenu';

// Loaded only for customers, so other roles never download the chat code.
const AssistantWidget = lazy(() => import('../../features/assistant/components/AssistantWidget'));

/**
 * Shell for the customer, merchant and admin areas: sidebar menu for the role, top bar with
 * tracking search, notification bell and account menu. On phones and tablets the sidebar is a
 * drawer opened from the menu button. A crash in one page shows an error in the page area only.
 */
export default function DashboardLayout() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Close the drawer with Escape.
  useEffect(() => {
    if (!drawerOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setDrawerOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [drawerOpen]);

  // The route guard only lets these roles in; the courier app has its own layout.
  if (!user || user.role === 'COURIER') return null;

  const sidebar = {
    sections: SIDEBAR[user.role],
    homePath: ROLE_HOME[user.role],
    showHelp: user.role !== 'DISPATCHER_ADMIN',
  };

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-slate-200 lg:block">
        <Sidebar {...sidebar} />
      </aside>

      {/* Mobile drawer */}
      {drawerOpen && (
        <div
          className="fixed inset-0 z-40 lg:hidden"
          role="dialog"
          aria-modal="true"
          aria-label="Menu"
        >
          <button
            type="button"
            aria-label="Close menu"
            className="absolute inset-0 bg-slate-900/40"
            onClick={() => setDrawerOpen(false)}
          />
          <aside className="absolute inset-y-0 left-0 w-72 max-w-[85%] shadow-xl">
            <Sidebar {...sidebar} onNavigate={() => setDrawerOpen(false)} />
            <button
              type="button"
              aria-label="Close menu"
              onClick={() => setDrawerOpen(false)}
              className="absolute top-3 right-3 rounded-full p-2 text-slate-500 hover:bg-slate-100"
            >
              <X className="size-5" aria-hidden="true" />
            </button>
          </aside>
        </div>
      )}

      <div className="flex min-h-screen flex-col lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6">
          <button
            type="button"
            aria-label="Open menu"
            aria-expanded={drawerOpen}
            onClick={() => setDrawerOpen(true)}
            className="-ml-1 rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
          >
            <Menu className="size-5" aria-hidden="true" />
          </button>
          <TrackingSearch className="w-full max-w-md" />
          <div className="ml-auto flex items-center gap-2 sm:gap-4">
            <NotificationBell />
            <UserMenu />
          </div>
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <ErrorBoundary variant="page" resetKey={pathname}>
            <Suspense fallback={<LoadingState />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>

      {user.role === 'CUSTOMER' && (
        <Suspense fallback={null}>
          <AssistantWidget />
        </Suspense>
      )}
    </div>
  );
}
