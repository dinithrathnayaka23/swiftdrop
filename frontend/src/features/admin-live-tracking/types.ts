// Feature: admin-live-tracking (Member 5). Admin: Deliveries Tracking (live).
import type { ParcelStatusCode } from '../../types/tracking';

/** Statuses shown on the board: parcels on their way. */
export const ACTIVE_STATUSES = ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'] as const;
export type ActiveStatus = (typeof ACTIVE_STATUSES)[number];

/** One row of GET /api/v1/admin/live/deliveries. Admin only. */
export interface LiveDeliveryRow {
  parcelId: string;
  trackingNumber: string;
  status: ParcelStatusCode;
  statusVersion: number;
  fromCity: string;
  toCity: string;
  /** null until the dispatch module can tell (CourierLookup); shown as "—" */
  courierName: string | null;
  /** ISO timestamp of the last status change */
  lastUpdatedAt: string;
}

/** GET /api/v1/admin/live/counts: parcels per status, every status present. */
export type LiveStatusCounts = Record<ParcelStatusCode, number>;
