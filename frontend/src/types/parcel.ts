import type { ParcelStatusCode } from './tracking';

/** Backend ParcelType: what the parcel contains. */
export type ParcelType = 'DOCUMENT' | 'PARCEL' | 'FRAGILE' | 'ELECTRONICS' | 'PERISHABLE';

/** Parcels per status, every status present (0 when none). Backend ParcelService.countByStatus. */
export type StatusCounts = Record<ParcelStatusCode, number>;

/** Spring Data's paged JSON shape (PagedModel). */
export interface Paged<T> {
  content: T[];
  page: {
    size: number;
    /** Zero-based page index. */
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface Address {
  contactName: string;
  contactPhone: string;
  addressLine1: string;
  addressLine2?: string | null;
  city: string;
  district: string;
  postalCode?: string | null;
}

export interface StatusHistoryItem {
  status: ParcelStatusCode;
  /** ISO timestamp. */
  changedAt: string;
  note?: string | null;
  failureReason?: string | null;
}

/** A row of the customer's "My Deliveries" and dashboard tables. */
export interface CustomerParcelSummary {
  id: string;
  trackingNumber: string;
  /** Order reference, when there is one. */
  orderReference?: string | null;
  destinationCity: string;
  destinationDistrict: string;
  bookedAt: string;
  status: ParcelStatusCode;
  quotedFee: number;
  feeCurrency: string;
}

export type PaymentStatus = 'PAID' | 'PENDING';

export interface CustomerParcelDetail {
  id: string;
  trackingNumber: string;
  orderReference?: string | null;
  bookedAt: string;
  lastUpdatedAt: string;
  status: ParcelStatusCode;
  pickupAddress: Address;
  dropoffAddress: Address;
  parcelType: ParcelType;
  contentDescription?: string | null;
  weightKg: number;
  lengthCm?: number | null;
  widthCm?: number | null;
  heightCm?: number | null;
  deliveryInstructions?: string | null;
  quotedFee: number;
  /** Fee after delivery; the same as the quote until the parcel is delivered. */
  finalFee?: number | null;
  feeCurrency: string;
  paymentMethod?: string | null;
  paymentStatus?: PaymentStatus | null;
  /** Oldest first. */
  history: StatusHistoryItem[];
}

export interface CustomerParcelFilters {
  /** Tracking number, destination or order reference. */
  search?: string;
  status?: ParcelStatusCode;
  /** ISO date (yyyy-mm-dd), inclusive. */
  bookedFrom?: string;
  bookedTo?: string;
  page?: number;
  size?: number;
}

/**
 * Whether dispatch has given a CREATED parcel to a courier (dispatch module, Member 6).
 * {@code null} once the parcel has been picked up.
 */
export type DispatchState = 'UNASSIGNED' | 'ASSIGNED';

/** Tabs of the admin Parcels page. */
export type AdminParcelTab =
  'ALL' | 'UNASSIGNED' | 'ASSIGNED' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED';

export type AdminParcelCounts = Record<AdminParcelTab, number>;

export interface AdminParcelRow {
  id: string;
  trackingNumber: string;
  /** {@code null} for a customer booking. */
  merchantName?: string | null;
  recipientName: string;
  destination: string;
  weightKg: number;
  status: ParcelStatusCode;
  dispatchState?: DispatchState | null;
  createdAt: string;
}

export interface AdminParcelNote {
  id: string;
  author: string;
  text: string;
  createdAt: string;
}

export interface AdminParcelDetail extends AdminParcelRow {
  serviceType: string;
  parcelType: ParcelType;
  contentDescription?: string | null;
  itemCount: number;
  paymentMethod: string;
  recipientPhone: string;
  recipientEmail?: string | null;
  dropoffAddress: Address;
  deliveryInstructions?: string | null;
  history: StatusHistoryItem[];
  notes: AdminParcelNote[];
}

export interface AdminParcelFilters {
  tab?: AdminParcelTab;
  /** Tracking number, recipient or merchant. */
  search?: string;
  status?: ParcelStatusCode;
  /** District of the delivery address. */
  area?: string;
  /** ISO date (yyyy-mm-dd), inclusive. */
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}

export interface ParcelsApi {
  getMyCounts(): Promise<StatusCounts>;
  listMyParcels(filters: CustomerParcelFilters): Promise<Paged<CustomerParcelSummary>>;
  /** Resolves to null when no parcel with this tracking number belongs to the caller. */
  getMyParcel(trackingNumber: string): Promise<CustomerParcelDetail | null>;
  downloadReceipt(parcelId: string): Promise<Blob>;

  getAdminCounts(): Promise<AdminParcelCounts>;
  listAdminParcels(filters: AdminParcelFilters): Promise<Paged<AdminParcelRow>>;
  getAdminParcel(parcelId: string): Promise<AdminParcelDetail | null>;
}
