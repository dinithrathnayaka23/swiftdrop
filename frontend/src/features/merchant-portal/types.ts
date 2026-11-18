import type { ParcelType, Paged, StatusCounts } from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';

export type { Paged, StatusCounts };

/** One row of the orders list (GET /merchant/orders). */
export interface MerchantOrderSummary {
  parcelId: string;
  trackingNumber: string;
  externalOrderReference: string | null;
  recipientName: string | null;
  deliveryCity: string | null;
  parcelType: ParcelType;
  status: ParcelStatusCode;
  quotedFee: number | null;
  feeCurrency: string | null;
  createdAt: string;
}

export interface MerchantOrderAddress {
  contactName: string;
  contactPhone: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  district: string | null;
  postalCode: string | null;
}

export interface MerchantOrderTimelineEntry {
  status: ParcelStatusCode;
  changedAt: string;
  note: string | null;
  failureReason: string | null;
  recipientName: string | null;
}

/** GET /merchant/orders/{trackingNumber}. The timeline is oldest first. */
export interface MerchantOrderDetail {
  parcelId: string;
  trackingNumber: string;
  externalOrderReference: string | null;
  pickupAddress: MerchantOrderAddress | null;
  dropoffAddress: MerchantOrderAddress | null;
  parcelType: ParcelType;
  weightKg: number | null;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
  contentDescription: string | null;
  deliveryInstructions: string | null;
  status: ParcelStatusCode;
  quotedFee: number | null;
  feeCurrency: string | null;
  createdAt: string;
  updatedAt: string;
  timeline: MerchantOrderTimelineEntry[];
}

export type ApiKeyStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED';

/** A listed API key. Never contains the key itself, only its public keyId. */
export interface ApiKey {
  apiCredentialId: string;
  keyId: string;
  status: ApiKeyStatus;
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
  lastUsedAt: string | null;
}

/** A newly created key. apiKey is shown once and can't be fetched again. */
export interface GeneratedApiKey {
  apiCredentialId: string;
  keyId: string;
  apiKey: string;
  createdAt: string;
}

export type MerchantApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

export interface MerchantProfile {
  merchantId: string;
  businessName: string;
  registrationNo: string;
  contactEmail: string;
  contactPhone: string;
  businessAddress: string;
  approvalStatus: MerchantApprovalStatus;
  approvedAt: string | null;
  updatedAt: string;
}

/** The profile fields a merchant may change (name and registration number are read-only). */
export interface UpdateMerchantProfile {
  contactEmail: string;
  contactPhone: string;
  businessAddress: string;
}
