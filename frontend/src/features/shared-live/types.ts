// Feature: shared-live (Member 5, Public Tracking & Real-time Updates).
// The live-update hook lives here so any screen can show parcel status changes without a refresh.
import type { ParcelStatusCode } from '../../types/tracking';

/**
 * Data of a `status` server-sent event: a parcel's status just changed.
 * Matches the backend record com.swiftdrop.realtime.dto.LiveStatusUpdate. No personal data.
 */
export interface LiveStatusUpdate {
  trackingNumber: string;
  /** null for the first status (CREATED) */
  previousStatus: ParcelStatusCode | null;
  status: ParcelStatusCode;
  /** Grows by one per change of the same parcel. Ignore an update not newer than what you show. */
  statusVersion: number;
  /** ISO timestamp */
  changedAt: string;
}

/** Name of the server-sent event that carries a LiveStatusUpdate. */
export const LIVE_STATUS_EVENT = 'status';

/**
 * Connection state shown to the user (for example a small "Live" badge).
 * - connecting: first connection or reconnecting after a drop
 * - live: receiving updates
 * - offline: gave up for now (too many failures); the page still shows the last loaded data
 */
export type LiveConnectionState = 'connecting' | 'live' | 'offline';
