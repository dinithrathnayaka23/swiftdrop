/** Must match com.swiftdrop.merchant.model.MerchantApprovalStatus on the backend. */
export const MERCHANT_STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'] as const;
export type MerchantStatus = (typeof MERCHANT_STATUSES)[number];

/** A merchant as the admin sees it (com.swiftdrop.merchant.dto.response.MerchantResponse). */
export interface Merchant {
  merchantId: string;
  businessName: string;
  registrationNo: string | null;
  contactEmail: string;
  contactPhone: string;
  businessAddress: string;
  approvalStatus: MerchantStatus;
  submittedAt: string;
  reviewedAt: string | null;
  rejectionReason: string | null;
}

/** Longest rejection reason the backend accepts (RejectMerchantRequest). */
export const MAX_REJECTION_REASON = 500;
