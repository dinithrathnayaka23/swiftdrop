export type NotificationCategory = 'DELIVERY' | 'ACCOUNT' | 'SYSTEM' | 'BILLING';

export type NotificationChannel = 'EMAIL' | 'SMS' | 'IN_APP';

export type NotificationDeliveryStatus = 'PENDING' | 'SENT' | 'FAILED';

export type DeliveryEventTrigger =
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED_ATTEMPT';

export interface NotificationItem {
  id: string;
  category: NotificationCategory;
  title: string;
  message: string;
  channel: NotificationChannel;
  deliveryStatus: NotificationDeliveryStatus;
  isRead: boolean;
  createdAt: string; // ISO 8601 string
  trackingNumber?: string;
  parcelId?: string;
  actionUrl?: string;
}

export interface NotificationPreferences {
  // Communication channels
  emailEnabled: boolean;
  smsEnabled: boolean;
  inAppEnabled: boolean;

  // Specific event triggers
  notifyOnPickedUp: boolean;
  notifyOnInTransit: boolean;
  notifyOnOutForDelivery: boolean;
  notifyOnDelivered: boolean;
  notifyOnFailedAttempt: boolean;

  // Marketing & System
  marketingEmails: boolean;
  systemMaintenanceAlerts: boolean;

  // Contact details for alerts
  notificationEmail?: string;
  notificationPhone?: string;
}

export type NotificationFilter = 'ALL' | 'UNREAD' | 'DELIVERY' | 'SYSTEM';

