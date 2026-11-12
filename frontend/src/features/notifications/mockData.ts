import type { NotificationItem, NotificationPreferences } from './types';

export const INITIAL_NOTIFICATIONS: NotificationItem[] = [
  {
    id: 'notif-1',
    category: 'DELIVERY',
    title: 'Parcel Out for Delivery',
    message: 'Your parcel SD2508220098 is out for delivery with courier Kamal. Expected delivery today by 4:30 PM.',
    channel: 'IN_APP',
    deliveryStatus: 'SENT',
    isRead: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 35).toISOString(), // 35 mins ago
    trackingNumber: 'SD2508220098',
  },
  {
    id: 'notif-2',
    category: 'DELIVERY',
    title: 'Parcel in Transit to Central Hub',
    message: 'Your parcel SD2508240012 departed Kandy Sorting Center and is in transit to Colombo Hub.',
    channel: 'EMAIL',
    deliveryStatus: 'SENT',
    isRead: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString(), // 3 hours ago
    trackingNumber: 'SD2508240012',
  },
  {
    id: 'notif-3',
    category: 'DELIVERY',
    title: 'Delivery Attempt Failed',
    message: 'Delivery attempt for parcel SD2508150021 failed. Reason: Recipient not available. A re-attempt has been scheduled.',
    channel: 'SMS',
    deliveryStatus: 'SENT',
    isRead: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 18).toISOString(), // 18 hours ago
    trackingNumber: 'SD2508150021',
  },
  {
    id: 'notif-4',
    category: 'DELIVERY',
    title: 'Parcel Delivered Successfully',
    message: 'Parcel SD2508200045 was successfully delivered. Proof of delivery and receipt are available for download.',
    channel: 'EMAIL',
    deliveryStatus: 'SENT',
    isRead: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 36).toISOString(), // 1.5 days ago
    trackingNumber: 'SD2508200045',
  },
  {
    id: 'notif-5',
    category: 'DELIVERY',
    title: 'Parcel Picked Up by Driver',
    message: 'Your shipment SD2508180176 was picked up from Colombo 03 and is being processed.',
    channel: 'IN_APP',
    deliveryStatus: 'SENT',
    isRead: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(), // 2 days ago
    trackingNumber: 'SD2508180176',
  },
  {
    id: 'notif-6',
    category: 'SYSTEM',
    title: 'Scheduled System Maintenance',
    message: 'SwiftDrop services will undergo scheduled infrastructure maintenance on Sunday from 01:00 AM to 03:00 AM IST. Tracking and booking may experience brief delays.',
    channel: 'IN_APP',
    deliveryStatus: 'SENT',
    isRead: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 72).toISOString(), // 3 days ago
  },
  {
    id: 'notif-7',
    category: 'ACCOUNT',
    title: 'Welcome to SwiftDrop!',
    message: 'Your customer account has been verified. You can now book parcels, monitor live status updates, and configure automatic notification preferences.',
    channel: 'EMAIL',
    deliveryStatus: 'SENT',
    isRead: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 120).toISOString(), // 5 days ago
  },
];

export const DEFAULT_PREFERENCES: NotificationPreferences = {
  emailEnabled: true,
  smsEnabled: true,
  inAppEnabled: true,
  notifyOnPickedUp: true,
  notifyOnInTransit: false,
  notifyOnOutForDelivery: true,
  notifyOnDelivered: true,
  notifyOnFailedAttempt: true,
  marketingEmails: false,
  systemMaintenanceAlerts: true,
  notificationEmail: 'customer@example.com',
  notificationPhone: '+94 77 123 4567',
};

