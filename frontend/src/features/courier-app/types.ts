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

/** The courier app's API, real or mock. Look-ups resolve to null when the delivery isn't the courier's. */
export interface CourierApi {
  getToday(): Promise<TodaysDeliveries>;
  getDelivery(parcelId: string): Promise<CourierDeliveryDetail | null>;
  findByTrackingNumber(trackingNumber: string): Promise<CourierDeliveryDetail | null>;
}
