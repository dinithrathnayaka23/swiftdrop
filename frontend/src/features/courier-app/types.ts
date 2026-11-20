import type { Paged, ParcelType } from '../../types/parcel';
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
  /** Delivered since midnight (admin's time zone); failed attempts don't count. */
  completedToday: number;
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

/** Longest recipient's name the backend accepts on a proof of delivery. */
export const MAX_RECIPIENT_NAME = 120;

/** Longest note the backend accepts with a proof of delivery. */
export const MAX_DELIVERY_NOTE = 500;

/** POST /courier/deliveries/{parcelId}/deliver (multipart/form-data) */
export interface DeliverRequest {
  recipientName: string;
  note?: string;
  /** Already resized in the browser (JPEG, at most 1600 px). */
  photo?: Blob;
}

/** How a finished delivery ended. */
export type DeliveryOutcome = 'DELIVERED' | 'FAILED_ATTEMPT';

/** A row of Completed Deliveries (GET /courier/deliveries/completed). */
export interface CompletedDelivery {
  parcelId: string;
  trackingNumber: string;
  outcome: DeliveryOutcome;
  /** ISO timestamp. */
  completedAt: string;
  recipientName: string;
  dropoffArea: string;
  dropoffCity: string;
  /** Delivered only: who took the parcel. */
  receivedBy: string | null;
  /** Failed attempt only. */
  failureReason: string | null;
  note: string | null;
  /** Delivered with a photo only; load it with getProofPhoto (needs the sign-in token). */
  proofPhotoUrl: string | null;
}

/** Filters for Completed Deliveries. Without dates, the server shows the last 7 days. */
export interface CompletedFilter {
  outcome?: DeliveryOutcome;
  /** yyyy-MM-dd, inclusive. */
  from?: string;
  /** yyyy-MM-dd, inclusive. */
  to?: string;
  /** Zero-based. */
  page: number;
  size?: number;
}

/** The courier's availability: only Online couriers get new deliveries. */
export type CourierStatus = 'ONLINE' | 'ON_BREAK' | 'OFFLINE';

/** The courier's own profile card (GET /courier/profile). */
export interface CourierProfile {
  courierId: string;
  fullName: string;
  email: string;
  phone: string;
  vehicleType: string;
  vehicleRegistration: string;
  status: CourierStatus;
  /** False once the dispatcher has deactivated the courier (they can't go online). */
  active: boolean;
  /** Zone names; empty means the courier can be given parcels anywhere. */
  serviceAreas: string[];
  assigned: number;
  inProgress: number;
  completedToday: number;
}

/** Dev builds only: the sample delivery just given to the courier. */
export interface SampleDelivery {
  parcelId: string;
  trackingNumber: string;
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
  /** Marks the delivery as delivered (400 bad name or photo, 409 not out for delivery, 413 too large). */
  deliver(parcelId: string, request: DeliverRequest): Promise<CourierDeliveryDetail>;
  /** The courier's deliveries and failed attempts, newest first. */
  getCompleted(filter: CompletedFilter): Promise<Paged<CompletedDelivery>>;
  /** A proof photo (`proofPhotoUrl`), fetched with the sign-in token. */
  getProofPhoto(proofPhotoUrl: string): Promise<Blob>;
  /** The signed-in courier's profile card. */
  getProfile(): Promise<CourierProfile>;
  /** Online / On break / Offline (409 when a deactivated courier asks to go online). */
  changeStatus(status: CourierStatus): Promise<CourierProfile>;
  /** Dev backend only: books a sample parcel and assigns it to the courier. */
  addSampleDelivery(): Promise<SampleDelivery>;
}
