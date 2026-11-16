import type { MerchantStatus } from './types';

/** Shown on badges and cards. */
export const MERCHANT_STATUS_LABELS: Record<MerchantStatus, string> = {
  PENDING: 'Pending review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
};

/** Badge colours per status. */
export const MERCHANT_STATUS_CLASSES: Record<MerchantStatus, string> = {
  PENDING: 'bg-amber-50 text-amber-700 ring-amber-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  REJECTED: 'bg-rose-50 text-rose-700 ring-rose-200',
  SUSPENDED: 'bg-slate-100 text-slate-600 ring-slate-200',
};
