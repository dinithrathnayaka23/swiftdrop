import { apiClient } from '../../api/client';
import { request } from '../../api/errors';
import { isMockApiEnabled } from '../../utils/env';
import {
  getNotifications,
  getPreferences,
  saveNotifications,
  savePreferences,
} from './notificationStore';
import type { NotificationItem, NotificationPreferences } from './types';

export const notificationsApi = {
  /** Fetch notifications list for current authenticated user */
  getNotifications: async (): Promise<NotificationItem[]> => {
    if (isMockApiEnabled()) {
      return getNotifications();
    }
    return request(() => apiClient.get<NotificationItem[]>('/customer/notifications'));
  },

  /** Fetch notification settings / preferences */
  getPreferences: async (): Promise<NotificationPreferences> => {
    if (isMockApiEnabled()) {
      return getPreferences();
    }
    return request(() => apiClient.get<NotificationPreferences>('/customer/notifications/preferences'));
  },

  /** Update notification settings / preferences */
  updatePreferences: async (
    preferences: NotificationPreferences,
  ): Promise<NotificationPreferences> => {
    if (isMockApiEnabled()) {
      savePreferences(preferences);
      return preferences;
    }
    return request(() =>
      apiClient.put<NotificationPreferences>('/customer/notifications/preferences', preferences),
    );
  },

  /** Mark a specific notification as read */
  markAsRead: async (notificationId: string): Promise<void> => {
    if (isMockApiEnabled()) {
      const current = getNotifications();
      saveNotifications(
        current.map((n) => (n.id === notificationId ? { ...n, isRead: true } : n)),
      );
      return;
    }
    return request(() =>
      apiClient.patch(`/customer/notifications/${encodeURIComponent(notificationId)}/read`),
    );
  },

  /** Mark all notifications as read */
  markAllAsRead: async (): Promise<void> => {
    if (isMockApiEnabled()) {
      const current = getNotifications();
      saveNotifications(current.map((n) => ({ ...n, isRead: true })));
      return;
    }
    return request(() => apiClient.post('/customer/notifications/read-all'));
  },
};

