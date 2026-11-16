import { useEffect, useState } from 'react';
import { apiClient } from '../../api/client';
import { isMockApiEnabled } from '../../utils/env';
import { DEFAULT_PREFERENCES, INITIAL_NOTIFICATIONS } from './mockData';
import type { NotificationItem, NotificationPreferences } from './types';

const NOTIFICATIONS_STORAGE_KEY = 'swiftdrop_notifications';
const PREFERENCES_STORAGE_KEY = 'swiftdrop_notification_preferences';
const SYNC_EVENT_NAME = 'swiftdrop_notifications_sync';

function loadStoredNotifications(): NotificationItem[] {
  try {
    const raw = localStorage.getItem(NOTIFICATIONS_STORAGE_KEY);
    if (!raw) return INITIAL_NOTIFICATIONS;
    return JSON.parse(raw);
  } catch {
    return INITIAL_NOTIFICATIONS;
  }
}

function loadStoredPreferences(): NotificationPreferences {
  try {
    const raw = localStorage.getItem(PREFERENCES_STORAGE_KEY);
    if (!raw) return DEFAULT_PREFERENCES;
    return JSON.parse(raw);
  } catch {
    return DEFAULT_PREFERENCES;
  }
}

function notifySubscribers() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(SYNC_EVENT_NAME));
  }
}

export function getNotifications(): NotificationItem[] {
  return loadStoredNotifications();
}

export function saveNotifications(items: NotificationItem[]) {
  try {
    localStorage.setItem(NOTIFICATIONS_STORAGE_KEY, JSON.stringify(items));
    notifySubscribers();
  } catch (err) {
    console.error('Failed to save notifications to localStorage', err);
  }
}

export function getPreferences(): NotificationPreferences {
  return loadStoredPreferences();
}

export function savePreferences(preferences: NotificationPreferences) {
  try {
    localStorage.setItem(PREFERENCES_STORAGE_KEY, JSON.stringify(preferences));
    notifySubscribers();
  } catch (err) {
    console.error('Failed to save notification preferences', err);
  }
  if (!isMockApiEnabled()) {
    void apiClient.put('/customer/notifications/preferences', preferences).catch(() => {
      // Fallback kept in localStorage if network request fails
    });
  }
}

export function markAsRead(notificationId: string) {
  const current = loadStoredNotifications();
  const updated = current.map((item) =>
    item.id === notificationId ? { ...item, isRead: true } : item,
  );
  saveNotifications(updated);
  if (!isMockApiEnabled()) {
    void apiClient
      .patch(`/customer/notifications/${encodeURIComponent(notificationId)}/read`)
      .catch(() => {});
  }
}

export function markAsUnread(notificationId: string) {
  const current = loadStoredNotifications();
  const updated = current.map((item) =>
    item.id === notificationId ? { ...item, isRead: false } : item,
  );
  saveNotifications(updated);
  if (!isMockApiEnabled()) {
    void apiClient
      .patch(`/customer/notifications/${encodeURIComponent(notificationId)}/unread`)
      .catch(() => {});
  }
}

export function markAllAsRead() {
  const current = loadStoredNotifications();
  const updated = current.map((item) => ({ ...item, isRead: true }));
  saveNotifications(updated);
  if (!isMockApiEnabled()) {
    void apiClient.post('/customer/notifications/read-all').catch(() => {});
  }
}

export function deleteNotification(notificationId: string) {
  const current = loadStoredNotifications();
  const updated = current.filter((item) => item.id !== notificationId);
  saveNotifications(updated);
  if (!isMockApiEnabled()) {
    void apiClient
      .delete(`/customer/notifications/${encodeURIComponent(notificationId)}`)
      .catch(() => {});
  }
}

export function clearAllNotifications() {
  saveNotifications([]);
  if (!isMockApiEnabled()) {
    void apiClient.delete('/customer/notifications').catch(() => {});
  }
}

export function resetToDefaults() {
  saveNotifications(INITIAL_NOTIFICATIONS);
  savePreferences(DEFAULT_PREFERENCES);
}

/**
 * React hook to subscribe to reactive notification updates across any component
 * (such as NotificationBell, Customer Notifications Page, or Dashboard).
 */
export function useNotifications() {
  const [notifications, setNotifications] = useState<NotificationItem[]>(loadStoredNotifications);
  const [preferences, setPreferencesState] =
    useState<NotificationPreferences>(loadStoredPreferences);

  useEffect(() => {
    function handleChange() {
      setNotifications(loadStoredNotifications());
      setPreferencesState(loadStoredPreferences());
    }

    window.addEventListener(SYNC_EVENT_NAME, handleChange);
    window.addEventListener('storage', handleChange);

    if (!isMockApiEnabled()) {
      void apiClient
        .get<NotificationItem[]>('/customer/notifications')
        .then((res) => {
          if (Array.isArray(res.data)) {
            saveNotifications(res.data);
          }
        })
        .catch(() => {});

      void apiClient
        .get<NotificationPreferences>('/customer/notifications/preferences')
        .then((res) => {
          if (res.data) {
            localStorage.setItem(PREFERENCES_STORAGE_KEY, JSON.stringify(res.data));
            notifySubscribers();
          }
        })
        .catch(() => {});
    }

    return () => {
      window.removeEventListener(SYNC_EVENT_NAME, handleChange);
      window.removeEventListener('storage', handleChange);
    };
  }, []);

  const unreadCount = notifications.filter((item) => !item.isRead).length;

  return {
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
  };
}
