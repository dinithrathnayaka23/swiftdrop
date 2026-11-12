import {
  Bell,
  CheckCheck,
  Search,
  Settings,
  SlidersHorizontal,
  Trash2,
  Truck,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import ConfirmDialog from '../../../components/feedback/ConfirmDialog';
import { EmptyState } from '../../../components/feedback/States';
import Button from '../../../components/ui/Button';
import { inputClasses } from '../../../components/ui/fieldStyles';
import { cn } from '../../../utils/cn';
import NotificationCard from '../components/NotificationCard';
import NotificationPreferencesTab from '../components/NotificationPreferencesTab';
import { useNotifications } from '../notificationStore';
import type { NotificationFilter } from '../types';

export default function NotificationsPage() {
  const {
    notifications,
    preferences,
    unreadCount,
    markAsRead,
    markAsUnread,
    markAllAsRead,
    deleteNotification,
    clearAllNotifications,
    savePreferences,
    resetToDefaults,
  } = useNotifications();

  const [activeTab, setActiveTab] = useState<'FEED' | 'PREFERENCES'>('FEED');
  const [feedCategory, setFeedCategory] = useState<NotificationFilter>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [showOnlyUnread, setShowOnlyUnread] = useState(false);
  const [clearDialogOpen, setClearDialogOpen] = useState(false);

  // Counts by category
  const deliveryCount = useMemo(
    () => notifications.filter((n) => n.category === 'DELIVERY').length,
    [notifications],
  );
  const systemCount = useMemo(
    () => notifications.filter((n) => n.category === 'SYSTEM' || n.category === 'ACCOUNT').length,
    [notifications],
  );

  // Filtered notifications
  const filteredNotifications = useMemo(() => {
    return notifications.filter((item) => {
      // Category filter
      if (feedCategory === 'DELIVERY' && item.category !== 'DELIVERY') return false;
      if (feedCategory === 'SYSTEM' && item.category !== 'SYSTEM' && item.category !== 'ACCOUNT') {
        return false;
      }
      if (feedCategory === 'UNREAD' && item.isRead) return false;

      // Unread only toggle
      if (showOnlyUnread && item.isRead) return false;

      // Search query filter
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const matchesTitle = item.title.toLowerCase().includes(q);
        const matchesMsg = item.message.toLowerCase().includes(q);
        const matchesTracking = item.trackingNumber?.toLowerCase().includes(q);
        if (!matchesTitle && !matchesMsg && !matchesTracking) {
          return false;
        }
      }

      return true;
    });
  }, [notifications, feedCategory, showOnlyUnread, searchQuery]);

  function handleToggleRead(id: string, isCurrentlyRead: boolean) {
    if (isCurrentlyRead) {
      markAsUnread(id);
    } else {
      markAsRead(id);
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      {/* Header & Page Title */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
              Notifications
            </h1>
            {unreadCount > 0 && (
              <span className="inline-flex items-center rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-brand-800">
                {unreadCount} unread
              </span>
            )}
          </div>
          <p className="mt-1 text-sm text-slate-500">
            Real-time delivery progress updates, courier notifications, and system alerts.
          </p>
        </div>

        {/* Global Action buttons */}
        <div className="flex flex-wrap items-center gap-2">
          {activeTab === 'FEED' && notifications.length > 0 && (
            <>
              <Button
                variant="outline"
                size="sm"
                onClick={markAllAsRead}
                disabled={unreadCount === 0}
                className="text-xs sm:text-sm"
              >
                <CheckCheck className="size-4" />
                Mark all as read
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setClearDialogOpen(true)}
                className="text-xs text-rose-600 hover:bg-rose-50 hover:text-rose-700 sm:text-sm"
              >
                <Trash2 className="size-4" />
                Clear all
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Summary Stat Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-medium uppercase tracking-wider">Total</span>
            <Bell className="size-4 text-slate-400" />
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">{notifications.length}</p>
          <span className="text-xs text-slate-400">Total received</span>
        </div>

        <div className="rounded-2xl border border-brand-200 bg-brand-50/30 p-4 shadow-xs">
          <div className="flex items-center justify-between text-brand-700">
            <span className="text-xs font-medium uppercase tracking-wider">Unread</span>
            <span className="size-2 rounded-full bg-brand-600" />
          </div>
          <p className="mt-2 text-2xl font-bold text-brand-800">{unreadCount}</p>
          <span className="text-xs text-brand-600">Pending attention</span>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-medium uppercase tracking-wider">Deliveries</span>
            <Truck className="size-4 text-blue-500" />
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">{deliveryCount}</p>
          <span className="text-xs text-slate-400">Parcel milestones</span>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-medium uppercase tracking-wider">Channels</span>
            <SlidersHorizontal className="size-4 text-slate-400" />
          </div>
          <p className="mt-2 text-base font-bold text-slate-800">
            {[
              preferences.emailEnabled ? 'Email' : null,
              preferences.smsEnabled ? 'SMS' : null,
              preferences.inAppEnabled ? 'In-App' : null,
            ]
              .filter(Boolean)
              .join(' · ') || 'None'}
          </p>
          <span className="text-xs text-slate-400">Active alerts</span>
        </div>
      </div>

      {/* Main Tabs Navigation */}
      <div className="border-b border-slate-200">
        <nav className="-mb-px flex gap-6" aria-label="Notifications views">
          <button
            type="button"
            onClick={() => setActiveTab('FEED')}
            className={cn(
              'inline-flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors',
              activeTab === 'FEED'
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700',
            )}
          >
            <Bell className="size-4" />
            <span>Activity History</span>
            <span
              className={cn(
                'ml-1 rounded-full px-2 py-0.5 text-xs',
                activeTab === 'FEED'
                  ? 'bg-brand-100 text-brand-800'
                  : 'bg-slate-100 text-slate-600',
              )}
            >
              {notifications.length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('PREFERENCES')}
            className={cn(
              'inline-flex items-center gap-2 border-b-2 py-3 text-sm font-semibold transition-colors',
              activeTab === 'PREFERENCES'
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700',
            )}
          >
            <Settings className="size-4" />
            <span>Preferences & Settings</span>
          </button>
        </nav>
      </div>

      {/* Tab 1: Activity Feed Content */}
      {activeTab === 'FEED' && (
        <div className="space-y-4">
          {/* Filter Bar & Search */}
          <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs lg:flex-row lg:items-center lg:justify-between">
            {/* Quick Filters */}
            <div className="flex flex-wrap items-center gap-1.5">
              <button
                type="button"
                onClick={() => {
                  setFeedCategory('ALL');
                  setShowOnlyUnread(false);
                }}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors',
                  feedCategory === 'ALL' && !showOnlyUnread
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200',
                )}
              >
                All ({notifications.length})
              </button>

              <button
                type="button"
                onClick={() => {
                  setFeedCategory('UNREAD');
                  setShowOnlyUnread(true);
                }}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors',
                  feedCategory === 'UNREAD' || showOnlyUnread
                    ? 'bg-brand-700 text-white'
                    : 'bg-brand-50 text-brand-800 hover:bg-brand-100',
                )}
              >
                Unread ({unreadCount})
              </button>

              <button
                type="button"
                onClick={() => {
                  setFeedCategory('DELIVERY');
                  setShowOnlyUnread(false);
                }}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors',
                  feedCategory === 'DELIVERY'
                    ? 'bg-blue-600 text-white'
                    : 'bg-blue-50 text-blue-700 hover:bg-blue-100',
                )}
              >
                Deliveries ({deliveryCount})
              </button>

              <button
                type="button"
                onClick={() => {
                  setFeedCategory('SYSTEM');
                  setShowOnlyUnread(false);
                }}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors',
                  feedCategory === 'SYSTEM'
                    ? 'bg-indigo-600 text-white'
                    : 'bg-indigo-50 text-indigo-700 hover:bg-indigo-100',
                )}
              >
                System & Account ({systemCount})
              </button>
            </div>

            {/* Search Input */}
            <div className="relative min-w-[240px] sm:w-72">
              <label htmlFor="search-notifications" className="sr-only">
                Search notifications
              </label>
              <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" />
              <input
                id="search-notifications"
                type="text"
                placeholder="Search tracking, keywords..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className={inputClasses(false, 'h-9.5 pl-9 text-xs sm:text-sm')}
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  className="absolute top-1/2 right-2.5 -translate-y-1/2 text-xs font-semibold text-slate-400 hover:text-slate-600"
                >
                  Clear
                </button>
              )}
            </div>
          </div>

          {/* Notifications List */}
          {filteredNotifications.length > 0 ? (
            <div className="space-y-3">
              {filteredNotifications.map((notification) => (
                <NotificationCard
                  key={notification.id}
                  notification={notification}
                  onToggleRead={handleToggleRead}
                  onDelete={deleteNotification}
                />
              ))}
            </div>
          ) : (
            <div className="rounded-2xl border border-slate-200 bg-white p-8">
              <EmptyState
                icon={Bell}
                title={
                  searchQuery || showOnlyUnread || feedCategory !== 'ALL'
                    ? 'No matching notifications found'
                    : 'No notifications yet'
                }
                message={
                  searchQuery || showOnlyUnread || feedCategory !== 'ALL'
                    ? 'Try clearing your search query or switching filters to view other alerts.'
                    : 'When your parcels are booked, picked up, or out for delivery, status updates will appear here.'
                }
                actionLabel="Reset Filters"
                onAction={() => {
                  setSearchQuery('');
                  setFeedCategory('ALL');
                  setShowOnlyUnread(false);
                }}
              />
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Preferences Tab Content */}
      {activeTab === 'PREFERENCES' && (
        <NotificationPreferencesTab
          preferences={preferences}
          onSave={savePreferences}
          onReset={resetToDefaults}
        />
      )}

      {/* Clear All Confirmation Modal */}
      <ConfirmDialog
        open={clearDialogOpen}
        title="Clear all notifications?"
        message="Are you sure you want to remove all notifications from your history? This action cannot be undone."
        confirmLabel="Clear All"
        cancelLabel="Cancel"
        onConfirm={() => {
          clearAllNotifications();
          setClearDialogOpen(false);
        }}
        onCancel={() => setClearDialogOpen(false)}
      />
    </div>
  );
}
