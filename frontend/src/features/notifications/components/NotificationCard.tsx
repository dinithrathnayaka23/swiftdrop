import {
  Bell,
  Check,
  CircleCheck,
  CircleX,
  ExternalLink,
  Info,
  Mail,
  MessageSquare,
  PackagePlus,
  ShieldCheck,
  Trash2,
  Truck,
  type LucideIcon,
} from 'lucide-react';
import { generatePath, Link } from 'react-router';
import { PATHS } from '../../../routes/paths';
import { cn } from '../../../utils/cn';
import type { NotificationItem } from '../types';

interface NotificationCardProps {
  notification: NotificationItem;
  onToggleRead: (id: string, isCurrentlyRead: boolean) => void;
  onDelete: (id: string) => void;
}

function getVisuals(notification: NotificationItem): {
  icon: LucideIcon;
  bgColor: string;
  textColor: string;
} {
  const text = (notification.title + ' ' + notification.message).toLowerCase();

  if (text.includes('delivered successfully') || text.includes('delivered')) {
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
  if (notification.category === 'ACCOUNT' || text.includes('welcome')) {
    return { icon: ShieldCheck, bgColor: 'bg-indigo-50', textColor: 'text-indigo-600' };
  }
  return { icon: Info, bgColor: 'bg-slate-100', textColor: 'text-slate-600' };
}

function formatRelativeTime(isoString: string): string {
  const diffMs = Date.now() - new Date(isoString).getTime();
  const diffMins = Math.max(0, Math.round(diffMs / (1000 * 60)));

  if (diffMins < 1) return 'Just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  const diffHours = Math.round(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.round(diffHours / 24);
  if (diffDays === 1) return 'Yesterday';
  if (diffDays < 7) return `${diffDays}d ago`;
  return new Date(isoString).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
  });
}

function getChannelBadge(channel: NotificationItem['channel']) {
  switch (channel) {
    case 'EMAIL':
      return {
        label: 'Email',
        icon: Mail,
        className: 'bg-slate-100 text-slate-700',
      };
    case 'SMS':
      return {
        label: 'SMS',
        icon: MessageSquare,
        className: 'bg-amber-50 text-amber-700',
      };
    case 'IN_APP':
    default:
      return {
        label: 'In-App',
        icon: Bell,
        className: 'bg-brand-50 text-brand-700',
      };
  }
}

export default function NotificationCard({
  notification,
  onToggleRead,
  onDelete,
}: NotificationCardProps) {
  const visual = getVisuals(notification);
  const Icon = visual.icon;
  const channelBadge = getChannelBadge(notification.channel);
  const ChannelIcon = channelBadge.icon;
  const relativeTime = formatRelativeTime(notification.createdAt);
  const fullDateTime = new Date(notification.createdAt).toLocaleString('en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });

  return (
    <article
      className={cn(
        'group relative flex flex-col gap-3 rounded-2xl border p-4.5 transition-all sm:flex-row sm:items-start sm:gap-4 sm:p-5',
        notification.isRead
          ? 'border-slate-200/80 bg-white hover:border-slate-300'
          : 'border-brand-200/90 bg-brand-50/25 shadow-xs hover:border-brand-300',
      )}
    >
      {/* Unread indicator dot */}
      {!notification.isRead && (
        <span
          className="absolute top-4 left-2.5 size-2 rounded-full bg-brand-600 sm:top-6 sm:left-3"
          title="Unread notification"
          aria-label="Unread"
        />
      )}

      {/* Category Icon */}
      <div
        className={cn(
          'flex size-11 shrink-0 items-center justify-center rounded-xl transition-transform group-hover:scale-105',
          visual.bgColor,
          visual.textColor,
        )}
      >
        <Icon className="size-5.5" strokeWidth={2} aria-hidden="true" />
      </div>

      {/* Content */}
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline justify-between gap-x-2 gap-y-1">
          <h3
            className={cn(
              'text-base leading-snug',
              notification.isRead ? 'font-medium text-slate-800' : 'font-semibold text-slate-900',
            )}
          >
            {notification.title}
          </h3>
          <time
            dateTime={notification.createdAt}
            title={fullDateTime}
            className="text-xs font-medium text-slate-400"
          >
            {relativeTime}
          </time>
        </div>

        <p className="mt-1 text-sm text-slate-600 leading-relaxed">{notification.message}</p>

        {/* Badges & Meta */}
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {/* Tracking reference link */}
          {notification.trackingNumber && (
            <Link
              to={generatePath(PATHS.customerDeliveryDetail, {
                trackingNumber: notification.trackingNumber,
              })}
              className="inline-flex items-center gap-1 rounded-md border border-brand-200 bg-brand-50/80 px-2.5 py-1 text-xs font-semibold text-brand-800 transition-colors hover:bg-brand-100"
            >
              <span>{notification.trackingNumber}</span>
              <ExternalLink className="size-3 text-brand-600" aria-hidden="true" />
            </Link>
          )}

          {/* Delivery channel tag */}
          <span
            className={cn(
              'inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium',
              channelBadge.className,
            )}
          >
            <ChannelIcon className="size-3" aria-hidden="true" />
            {channelBadge.label}
          </span>

          {/* Status info */}
          <span className="text-xs text-slate-400">· {fullDateTime}</span>
        </div>
      </div>

      {/* Action buttons */}
      <div className="flex items-center justify-end gap-1 border-t border-slate-100 pt-2 sm:border-0 sm:pt-0 sm:opacity-90">
        <button
          type="button"
          onClick={() => onToggleRead(notification.id, notification.isRead)}
          title={notification.isRead ? 'Mark as unread' : 'Mark as read'}
          aria-label={notification.isRead ? 'Mark as unread' : 'Mark as read'}
          className={cn(
            'inline-flex size-8.5 items-center justify-center rounded-lg border text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900',
            notification.isRead
              ? 'border-transparent text-slate-400'
              : 'border-brand-200 bg-white text-brand-700 hover:border-brand-300 hover:bg-brand-50',
          )}
        >
          {notification.isRead ? (
            <span className="size-2 rounded-full border border-slate-400 bg-transparent" />
          ) : (
            <Check className="size-4" strokeWidth={2.5} />
          )}
        </button>

        <button
          type="button"
          onClick={() => onDelete(notification.id)}
          title="Delete notification"
          aria-label="Delete notification"
          className="inline-flex size-8.5 items-center justify-center rounded-lg border border-transparent text-slate-400 transition-colors hover:border-red-100 hover:bg-red-50 hover:text-red-600"
        >
          <Trash2 className="size-4" />
        </button>
      </div>
    </article>
  );
}

