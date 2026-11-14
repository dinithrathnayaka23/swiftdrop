/**
 * Rows of the parcel_statuses table (backend ParcelStatusCode; report section 3.6). FAILED_ATTEMPT is
 * a failed delivery attempt waiting for the dispatcher (retry or close); only DELIVERED and FAILED
 * are final.
 */
export type ParcelStatusCode =
  | 'CREATED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED_ATTEMPT'
  | 'FAILED';

export interface TrackingEvent {
  statusCode: ParcelStatusCode;
  /** ISO timestamp (parcel_status_history.changed_at) */
  changedAt: string;
}

/** Public tracking response: GET /api/v1/public/tracking/{trackingNumber}. No personal data. */
export interface TrackingResult {
  trackingNumber: string;
  currentStatus: ParcelStatusCode;
  lastUpdatedAt: string;
  bookedAt: string;
  /** City of the pickup address */
  fromCity: string;
  /** City of the drop-off address */
  toCity: string;
  weightKg: number;
  /** Oldest first */
  history: TrackingEvent[];
}

export interface TrackingApi {
  /** Resolves to null when no parcel has this tracking number. */
  getTracking(trackingNumber: string): Promise<TrackingResult | null>;
}
