import type { ParcelType } from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';

/** A pickup or drop-off stop: who to meet, their phone and the address. */
export interface CourierStop {
  contactName: string;
  contactPhone: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  district: string;
  postalCode: string | null;
}

/** One row of Today's Deliveries (GET /courier/deliveries/today). */
export interface CourierDelivery {
  parcelId: string;
  trackingNumber: string;
  /** The recipient and where to deliver. */
  dropoff: CourierStop;
  /** Where to collect the parcel; only while it is still CREATED. */
  pickup: CourierStop | null;
  parcelType: ParcelType;
  weightKg: number;
  deliveryInstructions: string | null;
  currentStatus: ParcelStatusCode;
  /** The statuses the courier may move the parcel to next; empty once delivered or failed. */
  allowedActions: ParcelStatusCode[];
}

export interface CourierDeliveryCounts {
  total: number;
  /** Not started yet: waiting for pickup. */
  assigned: number;
  /** Picked up and on the way. */
  inProgress: number;
}

export interface TodaysDeliveries {
  counts: CourierDeliveryCounts;
  /** Oldest assignment first. */
  deliveries: CourierDelivery[];
}

export interface CourierTimelineEntry {
  status: ParcelStatusCode;
  /** ISO timestamp. */
  changedAt: string;
  note: string | null;
  failureReason: string | null;
}

/** Delivery Details (GET /courier/deliveries/{parcelId} and the scanner look-up). */
export interface CourierDeliveryDetail extends Omit<CourierDelivery, 'pickup'> {
  pickup: CourierStop;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
  contentDescription: string | null;
  /** ISO timestamp of the booking. */
  bookedAt: string;
  /** Oldest first. */
  timeline: CourierTimelineEntry[];
}

/** The steps recorded on Update Status; DELIVERED and FAILED have their own screens. */
export type CourierStepStatus = 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY';

export const COURIER_STEP_STATUSES: readonly CourierStepStatus[] = [
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
];

/** Longest note the backend accepts on a status update. */
export const MAX_STATUS_NOTE = 500;

/** POST /courier/deliveries/{parcelId}/status */
export interface UpdateStatusRequest {
  status: CourierStepStatus;
  note?: string;
}

/** Router state Today's Deliveries shows as a message, e.g. after a failed delivery is reported. */
export interface CourierNotice {
  notice: string;
}

/** One choice on the Failed Delivery form (GET /courier/failure-reasons). */
export interface FailureReason {
  code: string;
  label: string;
  /** The courier must add a note with this reason. */
  noteRequired: boolean;
}

/** Longest note the backend accepts with a failed delivery. */
export const MAX_FAILURE_NOTE = 500;

/** POST /courier/deliveries/{parcelId}/fail */
export interface ReportFailureRequest {
  reasonCode: string;
  note?: string;
}

/**
 * The courier app's API, real or mock. Look-ups resolve to null when the delivery isn't the
 * courier's. Changes reject with an ApiRequestError (404 not theirs, 409 the parcel can't take that
 * step now).
 */
export interface CourierApi {
  getToday(): Promise<TodaysDeliveries>;
  getDelivery(parcelId: string): Promise<CourierDeliveryDetail | null>;
  findByTrackingNumber(trackingNumber: string): Promise<CourierDeliveryDetail | null>;
  /** Records the step and resolves to the delivery as it is afterwards. */
  updateStatus(parcelId: string, request: UpdateStatusRequest): Promise<CourierDeliveryDetail>;
  /** The reasons a courier can give for a failed delivery, in display order. */
  getFailureReasons(): Promise<FailureReason[]>;
  /** Marks the delivery as failed (400 unknown reason or missing required note). */
  reportFailure(parcelId: string, request: ReportFailureRequest): Promise<CourierDeliveryDetail>;
}
