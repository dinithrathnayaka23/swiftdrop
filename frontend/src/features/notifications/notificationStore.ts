import { useEffect, useState } from 'react';
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
}

export function markAsRead(notificationId: string) {
  const current = loadStoredNotifications();
  const updated = current.map((item) =>
    item.id === notificationId ? { ...item, isRead: true } : item,
  );
  saveNotifications(updated);
}

export function markAsUnread(notificationId: string) {
  const current = loadStoredNotifications();
  const updated = current.map((item) =>
    item.id === notificationId ? { ...item, isRead: false } : item,
  );
  saveNotifications(updated);
}

export function markAllAsRead() {
  const current = loadStoredNotifications();
  const updated = current.map((item) => ({ ...item, isRead: true }));
  saveNotifications(updated);
}

export function deleteNotification(notificationId: string) {
  const current = loadStoredNotifications();
  const updated = current.filter((item) => item.id !== notificationId);
  saveNotifications(updated);
}

export function clearAllNotifications() {
  saveNotifications([]);
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

