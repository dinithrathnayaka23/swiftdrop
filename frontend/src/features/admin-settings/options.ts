import type { SelectOption } from '../../components/ui/SelectField';
import type {
  DateFormat,
  FailedDeliveryAction,
  ParcelTypeCode,
  TimeFormat,
} from '../../types/settings';
import { ITEMS_PER_PAGE } from '../../types/settings';

const DATE_FORMAT_LABELS: Record<DateFormat, string> = {
  DD_MMM_YYYY: 'DD MMM YYYY (24 Aug 2026)',
  DD_MM_YYYY: 'DD/MM/YYYY (24/08/2026)',
  MM_DD_YYYY: 'MM/DD/YYYY (08/24/2026)',
  YYYY_MM_DD: 'YYYY-MM-DD (2026-08-24)',
};

const TIME_FORMAT_LABELS: Record<TimeFormat, string> = {
  H12: '12 Hour (AM/PM)',
  H24: '24 Hour',
};

const PARCEL_TYPE_LABELS: Record<ParcelTypeCode, string> = {
  PARCEL: 'Standard parcel',
  DOCUMENT: 'Document',
  FRAGILE: 'Fragile',
  ELECTRONICS: 'Electronics',
  PERISHABLE: 'Perishable',
};

const FAILED_ACTION_LABELS: Record<FailedDeliveryAction, string> = {
  RETRY_DELIVERY: 'Retry delivery',
  RETURN_TO_SENDER: 'Return to sender / merchant',
  HOLD_FOR_COLLECTION: 'Hold at hub for collection',
};

function fromLabels<K extends string>(labels: Record<K, string>): SelectOption[] {
  return (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }));
}

export const DATE_FORMAT_OPTIONS = fromLabels(DATE_FORMAT_LABELS);
export const TIME_FORMAT_OPTIONS = fromLabels(TIME_FORMAT_LABELS);
export const PARCEL_TYPE_OPTIONS = fromLabels(PARCEL_TYPE_LABELS);
export const FAILED_ACTION_OPTIONS = fromLabels(FAILED_ACTION_LABELS);
export const ITEMS_PER_PAGE_OPTIONS: SelectOption[] = ITEMS_PER_PAGE.map((n) => ({
  value: String(n),
  label: String(n),
}));

/** Used when the browser can't list its time zones. */
const COMMON_TIME_ZONES = [
  'Asia/Colombo',
  'UTC',
  'Asia/Kolkata',
  'Asia/Dubai',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Australia/Sydney',
  'Europe/London',
  'Europe/Berlin',
  'America/New_York',
  'America/Los_Angeles',
];

/** "GMT+05:30" for a time zone, right now. */
function offsetOf(timeZone: string): string {
  try {
    const name = new Intl.DateTimeFormat('en-US', { timeZone, timeZoneName: 'longOffset' })
      .formatToParts(new Date())
      .find((part) => part.type === 'timeZoneName')?.value;
    return name === 'GMT' ? 'GMT+00:00' : (name ?? '');
  } catch {
    return '';
  }
}

/** Every time zone the browser knows, labelled like "(GMT+05:30) Colombo", plus the current one. */
export function timeZoneOptions(current: string): SelectOption[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (key: 'timeZone') => string[] };
  const zones = new Set(intl.supportedValuesOf?.('timeZone') ?? COMMON_TIME_ZONES);
  zones.add('UTC');
  zones.add(current);
  return [...zones].map((zone) => {
    const offset = offsetOf(zone);
    const place = zone.split('/').pop()?.replace(/_/g, ' ') ?? zone;
    const label = zone === 'UTC' ? 'UTC' : `${place} (${zone.split('/')[0]})`;
    return { value: zone, label: offset ? `(${offset}) ${label}` : label };
  });
}
