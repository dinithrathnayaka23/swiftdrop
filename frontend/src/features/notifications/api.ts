import { apiClient } from '../../api/client';
import { request } from '../../api/errors';
import { isMockApiEnabled } from '../../utils/env';
import {
  getNotifications,
  getPreferences,
  saveNotifications,
  savePreferences,
} from './notificationStore';
import type {
  CreateNotificationLogPayload,
  NotificationItem,
  NotificationLogItem,
  NotificationPreferences,
} from './types';

export const notificationsApi = {
  /** Fetch notifications list for current authenticated user */
  getNotifications: async (): Promise<NotificationItem[]> => {
    if (isMockApiEnabled()) {
      return getNotifications();
    }
    return request(() => apiClient.get<NotificationItem[]>('/customer/notifications'));
  },

  /** Fetch notification audit logs from the database with optional filters */
  getNotificationLogs: async (params?: {
    channel?: string;
    status?: string;
    parcelId?: string;
  }): Promise<NotificationLogItem[]> => {
    if (isMockApiEnabled()) {
      return getNotifications().map((n) => ({
        id: n.id,
        parcelId: n.parcelId,
        trackingNumber: n.trackingNumber,
        category: n.category,
        channel: n.channel,
        subject: n.title,
        message: n.message,
        deliveryStatus: n.deliveryStatus,
        isRead: n.isRead,
        createdAt: n.createdAt,
        sentAt: n.createdAt,
      }));
    }
    return request(() =>
      apiClient.get<NotificationLogItem[]>('/customer/notifications/logs', { params }),
    );
  },

  /** Persist a notification log entry to the database */
  createNotificationLog: async (
    payload: CreateNotificationLogPayload,
  ): Promise<NotificationLogItem> => {
    if (isMockApiEnabled()) {
      const created: NotificationItem = {
        id: `notif-${Date.now()}`,
        category: payload.parcelId ? 'DELIVERY' : 'SYSTEM',
        title: payload.subject,
        message: payload.message,
        channel: payload.channel,
        deliveryStatus: payload.deliveryStatus ?? 'SENT',
        isRead: false,
        createdAt: new Date().toISOString(),
        parcelId: payload.parcelId,
      };
      saveNotifications([created, ...getNotifications()]);
      return {
        id: created.id,
        parcelId: created.parcelId,
        category: created.category,
        channel: created.channel,
        subject: created.title,
        message: created.message,
        deliveryStatus: created.deliveryStatus,
        isRead: created.isRead,
        createdAt: created.createdAt,
        sentAt: created.createdAt,
      };
    }
    return request(() =>
      apiClient.post<NotificationLogItem>('/customer/notifications/logs', payload),
    );
  },

  /** Fetch notification settings / preferences */
  getPreferences: async (): Promise<NotificationPreferences> => {
    if (isMockApiEnabled()) {
      return getPreferences();
    }
    return request(() =>
      apiClient.get<NotificationPreferences>('/customer/notifications/preferences'),
    );
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
      saveNotifications(current.map((n) => (n.id === notificationId ? { ...n, isRead: true } : n)));
      return;
    }
    return request(() =>
      apiClient.patch(`/customer/notifications/${encodeURIComponent(notificationId)}/read`),
    );
  },

  /** Mark a specific notification as unread */
  markAsUnread: async (notificationId: string): Promise<void> => {
    if (isMockApiEnabled()) {
      const current = getNotifications();
      saveNotifications(
        current.map((n) => (n.id === notificationId ? { ...n, isRead: false } : n)),
      );
      return;
    }
    return request(() =>
      apiClient.patch(`/customer/notifications/${encodeURIComponent(notificationId)}/unread`),
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

  /** Delete a single notification from the database */
  deleteNotification: async (notificationId: string): Promise<void> => {
    if (isMockApiEnabled()) {
      const current = getNotifications();
      saveNotifications(current.filter((n) => n.id !== notificationId));
      return;
    }
    return request(() =>
      apiClient.delete(`/customer/notifications/${encodeURIComponent(notificationId)}`),
    );
  },

  /** Clear all notifications from the database */
  clearAllNotifications: async (): Promise<void> => {
    if (isMockApiEnabled()) {
      saveNotifications([]);
      return;
    }
    return request(() => apiClient.delete('/customer/notifications'));
  },
};
