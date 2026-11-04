import { Bell } from 'lucide-react';
import { Link } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { PATHS } from '../../routes/paths';

const bellClasses =
  'relative inline-flex size-10 items-center justify-center rounded-full text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-2 focus-visible:outline-brand-600';

/**
 * Notification bell shown in every top bar. Owned by Member 10 (notifications): this is the
 * pre-created stub that the layouts already render. Replace the body with the unread count and
 * dropdown; keep the component name and props so the layouts don't need to change.
 */
export default function NotificationBell() {
  const { user } = useAuth();

  if (user?.role === 'CUSTOMER') {
    return (
      <Link to={PATHS.customerNotifications} aria-label="Notifications" className={bellClasses}>
        <Bell className="size-5" aria-hidden="true" />
      </Link>
    );
  }

  return (
    <button
      type="button"
      aria-label="Notifications"
      title="No new notifications"
      className={bellClasses}
    >
      <Bell className="size-5" aria-hidden="true" />
    </button>
  );
}
