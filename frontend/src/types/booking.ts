export type ParcelType = 'DOCUMENT' | 'PARCEL' | 'FRAGILE' | 'ELECTRONICS' | 'PERISHABLE';

/** One pickup or drop-off address, as typed by the customer. */
export interface BookingAddress {
  contactName: string;
  contactPhone: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  district: string;
  postalCode?: string;
}

/** Sent to both the estimate and the confirm call. */
export interface BookingRequest {
  pickup: BookingAddress;
  dropoff: BookingAddress;
  parcelType: ParcelType;
  weightKg: number;
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
  contentDescription?: string;
  deliveryInstructions?: string;
}

export interface FeeLine {
  label: string;
  amount: number;
}

export interface FeeEstimate {
  totalFee: number;
  currency: string;
  lines: FeeLine[];
}

export interface BookingConfirmation {
  parcelId: string;
  trackingNumber: string;
  status: string;
  totalFee: number;
  currency: string;
  bookedAt: string;
}

export interface BookingApi {
  /** The fee for a booking. Nothing is saved. */
  estimate: (booking: BookingRequest) => Promise<FeeEstimate>;
  /** Confirms the booking. The server calculates the fee again. */
  book: (booking: BookingRequest) => Promise<BookingConfirmation>;
}
