import type {
  DateFormat,
  FailedDeliveryAction,
  ParcelTypeCode,
  SystemSettings,
  TimeFormat,
  UpdateSystemSettingsRequest,
} from '../../types/settings';
import { validateEmail, type FieldErrors } from '../../utils/validation';

/** Text and dropdown fields, as strings (what the inputs hold). */
export type SettingsTextValues = {
  companyName: string;
  contactEmail: string;
  contactPhone: string;
  companyAddress: string;
  timeZone: string;
  dateFormat: string;
  timeFormat: string;
  itemsPerPage: string;
  defaultParcelType: string;
  maxParcelWeightKg: string;
  maxParcelDimensionCm: string;
  failedDeliveryAction: string;
};

/** On/off settings. */
export interface SettingsToggles {
  autoAssignCourier: boolean;
  notifyFailedDeliveries: boolean;
  notifyNewMerchants: boolean;
  notifyCourierIssues: boolean;
  dailySummaryReport: boolean;
  maintenanceMode: boolean;
}

export type SettingsTab = 'general' | 'parcels' | 'notifications' | 'maintenance';

/** Which tab each field is on, so a save error can open the right tab. */
export const FIELD_TAB: Record<keyof SettingsTextValues, SettingsTab> = {
  companyName: 'general',
  contactEmail: 'general',
  contactPhone: 'general',
  companyAddress: 'general',
  timeZone: 'general',
  dateFormat: 'general',
  timeFormat: 'general',
  itemsPerPage: 'general',
  defaultParcelType: 'parcels',
  maxParcelWeightKg: 'parcels',
  maxParcelDimensionCm: 'parcels',
  failedDeliveryAction: 'parcels',
};

export function toTextValues(s: SystemSettings): SettingsTextValues {
  return {
    companyName: s.companyName,
    contactEmail: s.contactEmail,
    contactPhone: s.contactPhone ?? '',
    companyAddress: s.companyAddress ?? '',
    timeZone: s.timeZone,
    dateFormat: s.dateFormat,
    timeFormat: s.timeFormat,
    itemsPerPage: String(s.itemsPerPage),
    defaultParcelType: s.defaultParcelType,
    maxParcelWeightKg: String(s.maxParcelWeightKg),
    maxParcelDimensionCm: String(s.maxParcelDimensionCm),
    failedDeliveryAction: s.failedDeliveryAction,
  };
}

export function toToggles(s: SystemSettings): SettingsToggles {
  return {
    autoAssignCourier: s.autoAssignCourier,
    notifyFailedDeliveries: s.notifyFailedDeliveries,
    notifyNewMerchants: s.notifyNewMerchants,
    notifyCourierIssues: s.notifyCourierIssues,
    dailySummaryReport: s.dailySummaryReport,
    maintenanceMode: s.maintenanceMode,
  };
}

/** "+94 77 123 4567" -> "+94771234567" */
export function normalizePhone(value: string): string {
  return value.replace(/[\s()-]/g, '');
}

export function toRequest(
  version: number,
  text: SettingsTextValues,
  toggles: SettingsToggles,
): UpdateSystemSettingsRequest {
  const phone = normalizePhone(text.contactPhone);
  return {
    version,
    companyName: text.companyName.trim(),
    contactEmail: text.contactEmail.trim(),
    contactPhone: phone || null,
    companyAddress: text.companyAddress.trim() || null,
    timeZone: text.timeZone,
    dateFormat: text.dateFormat as DateFormat,
    timeFormat: text.timeFormat as TimeFormat,
    itemsPerPage: Number(text.itemsPerPage),
    defaultParcelType: text.defaultParcelType as ParcelTypeCode,
    maxParcelWeightKg: Number(text.maxParcelWeightKg),
    maxParcelDimensionCm: Number(text.maxParcelDimensionCm),
    failedDeliveryAction: text.failedDeliveryAction as FailedDeliveryAction,
    ...toggles,
  };
}

function validateLimit(value: string, label: string, unit: string, decimals: number) {
  const trimmed = value.trim();
  if (!trimmed) return `${label} is required`;
  if (!/^\d+(\.\d+)?$/.test(trimmed)) return `Enter a number`;
  const number = Number(trimmed);
  if (number <= 0) return `${label} must be more than 0`;
  if (number > 1000) return `${label} can be at most 1000 ${unit}`;
  if ((trimmed.split('.')[1]?.length ?? 0) > decimals)
    return `Use at most ${decimals} decimal places`;
  return undefined;
}

/** Same rules as the backend, so most mistakes are caught before saving. */
export function validateSettings(v: SettingsTextValues): FieldErrors<keyof SettingsTextValues> {
  const phone = normalizePhone(v.contactPhone);
  return {
    companyName: !v.companyName.trim()
      ? 'Company name is required'
      : v.companyName.trim().length > 120
        ? 'Company name must be at most 120 characters'
        : undefined,
    contactEmail: validateEmail(v.contactEmail),
    contactPhone:
      phone && !/^\+[1-9]\d{6,14}$/.test(phone)
        ? 'Enter the number with its country code, e.g. +94 77 123 4567'
        : undefined,
    companyAddress:
      v.companyAddress.trim().length > 255 ? 'Address must be at most 255 characters' : undefined,
    maxParcelWeightKg: validateLimit(v.maxParcelWeightKg, 'Maximum parcel weight', 'kg', 3),
    maxParcelDimensionCm: validateLimit(v.maxParcelDimensionCm, 'Maximum dimension', 'cm', 2),
  };
}
