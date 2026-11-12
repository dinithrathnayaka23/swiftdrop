import {
  ArrowRight,
  Bell,
  Check,
  CircleCheck,
  CircleX,
  Info,
  PackagePlus,
  Settings,
  Truck,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { generatePath, Link, useNavigate } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { PATHS } from '../../routes/paths';
import { cn } from '../../utils/cn';
import { useNotifications } from '../notifications/notificationStore';
import type { NotificationItem } from '../notifications/types';

function getVisual(notification: NotificationItem): {
  icon: LucideIcon;
  bgColor: string;
  textColor: string;
} {
  const text = (notification.title + ' ' + notification.message).toLowerCase();
  if (text.includes('delivered')) {
    return { icon: CircleCheck, bgColor: 'bg-emerald-50', textColor: 'text-emerald-600' };
  }
  if (text.includes('failed') || text.includes('exception')) {
    return { icon: CircleX, bgColor: 'bg-rose-50', textColor: 'text-rose-600' };
  }
  if (text.includes('out for delivery') || text.includes('in transit')) {
    return { icon: Truck, bgColor: 'bg-blue-50', textColor: 'text-blue-600' };
  }
  if (text.includes('picked up')) {
    return { icon: PackagePlus, bgColor: 'bg-amber-50', textColor: 'text-amber-600' };
  }
  return { icon: Info, bgColor: 'bg-slate-100', textColor: 'text-slate-600' };
}

function formatRelativeTime(isoString: string): string {
  const diffMs = Date.now() - new Date(isoString).getTime();
  const diffMins = Math.max(0, Math.round(diffMs / (1000 * 60)));
  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  const diffHours = Math.round(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  return `${Math.round(diffHours / 24)}d ago`;
}

/**
 * Notification bell shown in every top bar.
 * Owned by Member 10 (Notifications, Events & Deployment).
 * Features live unread badge, interactive dropdown popover, quick actions, and deep links.
 */
export default function NotificationBell() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { notifications, unreadCount, markAsRead, markAllAsRead } = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleKeyDown);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const recentList = notifications.slice(0, 5);

  function handleItemClick(item: NotificationItem) {
    markAsRead(item.id);
    setIsOpen(false);

    if (user?.role === 'CUSTOMER') {
      if (item.trackingNumber) {
        navigate(
          generatePath(PATHS.customerDeliveryDetail, {
            trackingNumber: item.trackingNumber,
          }),
        );
      } else {
        navigate(PATHS.customerNotifications);
      }
    }
  }

  return (
    <div className="relative" ref={containerRef}>
      {/* Bell Button */}
      <button
        type="button"
        aria-label={`Notifications (${unreadCount} unread)`}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        onClick={() => setIsOpen((prev) => !prev)}
        className={cn(
          'relative inline-flex size-10 items-center justify-center rounded-full text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-2 focus-visible:outline-brand-600',
          isOpen && 'bg-slate-100 text-slate-900',
        )}
      >
        <Bell className="size-5" aria-hidden="true" />

        {/* Unread Badge Counter */}
        {unreadCount > 0 && (
          <span className="absolute top-1.5 right-1.5 flex size-4.5 items-center justify-center rounded-full bg-brand-600 text-[10px] font-bold text-white shadow-xs animate-in zoom-in-50">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {/* Floating Dropdown Popover */}
      {isOpen && (
        <div
          role="dialog"
          aria-label="Notification center"
          className="absolute right-0 z-50 mt-2 w-80 max-w-[calc(100vw-2rem)] origin-top-right rounded-2xl border border-slate-200 bg-white shadow-xl ring-1 ring-black/5 sm:w-96 animate-in fade-in-0 zoom-in-95"
        >
          {/* Header */}
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 sm:px-5">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-slate-900">Notifications</h2>
              {unreadCount > 0 && (
                <span className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">
                  {unreadCount} new
                </span>
              )}
            </div>

            {unreadCount > 0 && (
              <button
                type="button"
                onClick={markAllAsRead}
                className="inline-flex items-center gap-1 text-xs font-semibold text-brand-700 hover:text-brand-800"
              >
                <Check className="size-3" />
                Mark all read
              </button>
            )}
          </div>

          {/* List of Recent Items */}
          <div className="max-h-96 divide-y divide-slate-100 overflow-y-auto">
            {recentList.length > 0 ? (
              recentList.map((item) => {
                const visual = getVisual(item);
                const Icon = visual.icon;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => handleItemClick(item)}
                    className={cn(
                      'flex w-full items-start gap-3 p-3.5 text-left transition-colors hover:bg-slate-50/80 sm:px-4',
                      !item.isRead && 'bg-brand-50/20',
                    )}
                  >
                    {/* Visual Icon */}
                    <div
                      className={cn(
                        'flex size-9 shrink-0 items-center justify-center rounded-xl',
                        visual.bgColor,
                        visual.textColor,
                      )}
                    >
                      <Icon className="size-4" strokeWidth={2} />
                    </div>

                    {/* Text Details */}
                    <div className="min-w-0 flex-1">
                      <div className="flex items-baseline justify-between gap-1">
                        <p
                          className={cn(
                            'truncate text-xs',
                            item.isRead
                              ? 'font-medium text-slate-700'
                              : 'font-semibold text-slate-900',
                          )}
                        >
                          {item.title}
                        </p>
                        <time className="shrink-0 text-[11px] text-slate-400">
                          {formatRelativeTime(item.createdAt)}
                        </time>
                      </div>

                      <p className="mt-0.5 line-clamp-2 text-xs text-slate-500 leading-relaxed">
                        {item.message}
                      </p>

                      {item.trackingNumber && (
                        <span className="mt-1.5 inline-block rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] font-medium text-slate-600">
                          {item.trackingNumber}
                        </span>
                      )}
                    </div>

                    {/* Unread blue dot */}
                    {!item.isRead && (
                      <span
                        className="mt-1.5 size-2 shrink-0 rounded-full bg-brand-600"
                        title="Unread"
                      />
                    )}
                  </button>
                );
              })
            ) : (
              <div className="py-8 text-center text-slate-400">
                <Bell className="mx-auto size-8 stroke-1 text-slate-300" />
                <p className="mt-2 text-xs font-medium text-slate-600">No notifications yet</p>
                <p className="text-[11px] text-slate-400">Updates will appear here in real time.</p>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/70 px-4 py-2.5 sm:px-5">
            {user?.role === 'CUSTOMER' ? (
              <>
                <Link
                  to={PATHS.customerNotifications}
                  onClick={() => setIsOpen(false)}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-brand-700 hover:text-brand-800"
                >
                  <span>View all notifications</span>
                  <ArrowRight className="size-3.5" />
                </Link>
                <Link
                  to={PATHS.customerNotifications}
                  onClick={() => setIsOpen(false)}
                  title="Notification settings"
                  className="rounded-lg p-1 text-slate-400 hover:bg-slate-200/60 hover:text-slate-600"
                >
                  <Settings className="size-4" />
                </Link>
              </>
            ) : (
              <span className="text-[11px] text-slate-400">Notifications are up to date</span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
