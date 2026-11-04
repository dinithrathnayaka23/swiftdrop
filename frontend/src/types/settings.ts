// Must match com.swiftdrop.settings on the backend (/api/v1/admin/settings, /api/v1/public/settings).

export const DATE_FORMATS = ['DD_MMM_YYYY', 'DD_MM_YYYY', 'MM_DD_YYYY', 'YYYY_MM_DD'] as const;
export type DateFormat = (typeof DATE_FORMATS)[number];

export const TIME_FORMATS = ['H12', 'H24'] as const;
export type TimeFormat = (typeof TIME_FORMATS)[number];

export const ITEMS_PER_PAGE = [10, 20, 50, 100] as const;

/** Same values as parcels.parcel_type. */
export const PARCEL_TYPES = ['DOCUMENT', 'PARCEL', 'FRAGILE', 'ELECTRONICS', 'PERISHABLE'] as const;
export type ParcelTypeCode = (typeof PARCEL_TYPES)[number];

export const FAILED_DELIVERY_ACTIONS = [
  'RETRY_DELIVERY',
  'RETURN_TO_SENDER',
  'HOLD_FOR_COLLECTION',
] as const;
export type FailedDeliveryAction = (typeof FAILED_DELIVERY_ACTIONS)[number];

/** Settings anyone may read (GET /public/settings). */
export interface PublicSettings {
  companyName: string;
  contactEmail: string;
  contactPhone: string | null;
  companyAddress: string | null;
  timeZone: string;
  dateFormat: DateFormat;
  timeFormat: TimeFormat;
  itemsPerPage: number;
  defaultParcelType: ParcelTypeCode;
  maxParcelWeightKg: number;
  maxParcelDimensionCm: number;
  maintenanceMode: boolean;
}

/** Every setting, as the admin sees it (GET /admin/settings). */
export interface SystemSettings extends PublicSettings {
  failedDeliveryAction: FailedDeliveryAction;
  autoAssignCourier: boolean;
  notifyFailedDeliveries: boolean;
  notifyNewMerchants: boolean;
  notifyCourierIssues: boolean;
  dailySummaryReport: boolean;
  /** Send back unchanged when saving; the save is refused (409) if someone saved in between. */
  version: number;
  updatedAt: string;
}

/** PUT /admin/settings: the whole form. */
export type UpdateSystemSettingsRequest = Omit<SystemSettings, 'updatedAt'>;

/** Settings used before the server has answered (the backend's defaults). */
export const DEFAULT_PUBLIC_SETTINGS: PublicSettings = {
  companyName: 'SwiftDrop',
  contactEmail: 'support@swiftdrop.lk',
  contactPhone: null,
  companyAddress: null,
  timeZone: 'Asia/Colombo',
  dateFormat: 'DD_MMM_YYYY',
  timeFormat: 'H12',
  itemsPerPage: 10,
  defaultParcelType: 'PARCEL',
  maxParcelWeightKg: 50,
  maxParcelDimensionCm: 120,
  maintenanceMode: false,
};
