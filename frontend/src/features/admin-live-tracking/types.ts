// Feature: admin-live-tracking (Member 5). Admin: Deliveries Tracking (live).
import type { ParcelType } from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';

/**
 * One row of GET /api/v1/admin/live/deliveries (backend LiveDeliveryResponse). Admin only: has the
 * recipient's name and phone.
 */
export interface LiveDeliveryRow {
  parcelId: string;
  trackingNumber: string;
  status: ParcelStatusCode;
  /** 1 for CREATED, +1 per change. A live update with a higher version replaces the row. */
  statusVersion: number;
  recipientName: string;
  recipientPhone: string;
  /** null for a customer booking */
  merchantName: string | null;
  /** null until the dispatch module can tell (CourierLookup); shown as "—" */
  courierName: string | null;
  destinationCity: string;
  /** ISO timestamps */
  bookedAt: string;
  lastUpdatedAt: string;
}

/** GET /api/v1/admin/live/deliveries/{trackingNumber} (backend LiveDeliveryDetailResponse). */
export interface LiveDeliveryDetail {
  parcelId: string;
  trackingNumber: string;
  status: ParcelStatusCode;
  statusVersion: number;
  bookedAt: string;
  lastUpdatedAt: string;
  recipientName: string;
  recipientPhone: string;
  dropoffAddress: {
    addressLine1: string;
    addressLine2: string | null;
    city: string;
    district: string;
    postalCode: string | null;
  };
  fromCity: string;
  merchantName: string | null;
  courierName: string | null;
  parcelType: ParcelType;
  weightKg: number;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
  contentDescription: string | null;
  deliveryInstructions: string | null;
  /** Oldest first */
  history: { status: ParcelStatusCode; changedAt: string }[];
}

/** GET /api/v1/admin/live/counts: parcels per status, every status present. */
export type LiveStatusCounts = Record<ParcelStatusCode, number>;

/** Query of the deliveries table. Empty values are left out of the request. */
export interface LiveDeliveryFilters {
  status?: ParcelStatusCode;
  /** Part of a tracking number, recipient or merchant name */
  search?: string;
  /** yyyy-MM-dd, booking days in the System Settings time zone */
  bookedFrom?: string;
  bookedTo?: string;
  /** Zero-based */
  page: number;
  size: number;
}
