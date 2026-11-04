import type { SystemSettings } from '../../types/settings';
import { toRequest, toTextValues, toToggles, validateSettings } from './settingsForm';

const SETTINGS: SystemSettings = {
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
  failedDeliveryAction: 'RETRY_DELIVERY',
  autoAssignCourier: false,
  notifyFailedDeliveries: true,
  notifyNewMerchants: true,
  notifyCourierIssues: true,
  dailySummaryReport: false,
  maintenanceMode: false,
  version: 4,
  updatedAt: '2026-11-04T08:00:00Z',
};

describe('settings form', () => {
  it('round-trips the saved settings into a request', () => {
    const request = toRequest(SETTINGS.version, toTextValues(SETTINGS), toToggles(SETTINGS));

    const { updatedAt, ...expected } = SETTINGS;
    void updatedAt;
    expect(request).toEqual(expected);
  });

  it('cleans up text before saving', () => {
    const text = {
      ...toTextValues(SETTINGS),
      companyName: '  SwiftDrop Lanka ',
      contactPhone: '+94 77 123-4567',
      companyAddress: '   ',
      maxParcelWeightKg: '30.5',
    };

    const request = toRequest(7, text, toToggles(SETTINGS));

    expect(request).toMatchObject({
      version: 7,
      companyName: 'SwiftDrop Lanka',
      contactPhone: '+94771234567',
      companyAddress: null,
      maxParcelWeightKg: 30.5,
    });
  });

  it('accepts the saved settings as they are', () => {
    expect(Object.values(validateSettings(toTextValues(SETTINGS))).filter(Boolean)).toEqual([]);
  });

  it.each([
    ['companyName', '   ', 'Company name is required'],
    ['contactEmail', 'not-an-email', 'Enter a valid email address'],
    ['contactPhone', '0771234567', 'Enter the number with its country code, e.g. +94 77 123 4567'],
    ['maxParcelWeightKg', '0', 'Maximum parcel weight must be more than 0'],
    ['maxParcelWeightKg', '1001', 'Maximum parcel weight can be at most 1000 kg'],
    ['maxParcelWeightKg', 'ten', 'Enter a number'],
    ['maxParcelDimensionCm', '12.345', 'Use at most 2 decimal places'],
  ] as const)('explains a wrong %s (%j)', (field, value, message) => {
    const errors = validateSettings({ ...toTextValues(SETTINGS), [field]: value });
    expect(errors[field]).toBe(message);
  });
});
