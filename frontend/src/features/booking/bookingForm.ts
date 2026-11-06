import type { useForm } from '../../hooks/useForm';
import type { BookingAddress, BookingRequest, ParcelType } from '../../types/booking';
import { PARCEL_TYPES } from '../../types/settings';
import { DEFAULT_COUNTRY_CODE, toE164 } from '../../utils/phone';
import { validatePersonName, validatePhone, type FieldErrors } from '../../utils/validation';
import { DISTRICTS } from './districts';

export const BOOKING_STEPS = [
  { title: 'Pickup & Drop-off', description: 'Addresses and contact details' },
  { title: 'Parcel Details', description: 'Size, weight and contents' },
  { title: 'Review & Confirm', description: 'Check details and book' },
] as const;

// ---- Step 1: addresses ----

export type AddressPrefix = 'pickup' | 'dropoff';
type AddressSuffix =
  'Name' | 'CountryCode' | 'Phone' | 'Line1' | 'Line2' | 'City' | 'District' | 'PostalCode';

export type AddressValues = {
  pickupName: string;
  pickupCountryCode: string;
  pickupPhone: string;
  pickupLine1: string;
  pickupLine2: string;
  pickupCity: string;
  pickupDistrict: string;
  pickupPostalCode: string;
  dropoffName: string;
  dropoffCountryCode: string;
  dropoffPhone: string;
  dropoffLine1: string;
  dropoffLine2: string;
  dropoffCity: string;
  dropoffDistrict: string;
  dropoffPostalCode: string;
};

export type AddressForm = ReturnType<typeof useForm<AddressValues>>;

/** The field name for one part of the pickup or drop-off address, e.g. ('pickup', 'City'). */
export function addressKey(prefix: AddressPrefix, suffix: AddressSuffix): keyof AddressValues {
  return `${prefix}${suffix}` as keyof AddressValues;
}

export function initialAddressValues(): AddressValues {
  return {
    pickupName: '',
    pickupCountryCode: DEFAULT_COUNTRY_CODE,
    pickupPhone: '',
    pickupLine1: '',
    pickupLine2: '',
    pickupCity: '',
    pickupDistrict: '',
    pickupPostalCode: '',
    dropoffName: '',
    dropoffCountryCode: DEFAULT_COUNTRY_CODE,
    dropoffPhone: '',
    dropoffLine1: '',
    dropoffLine2: '',
    dropoffCity: '',
    dropoffDistrict: '',
    dropoffPostalCode: '',
  };
}

function requiredText(value: string, label: string, max: number): string | undefined {
  const text = value.trim();
  if (!text) return `${label} is required`;
  if (text.length > max) return `${label} must be at most ${max} characters`;
  return undefined;
}

function optionalText(value: string, label: string, max: number): string | undefined {
  return value.trim().length > max ? `${label} must be at most ${max} characters` : undefined;
}

function addressErrors(
  values: AddressValues,
  prefix: AddressPrefix,
  nameLabel: string,
): FieldErrors<keyof AddressValues> {
  const get = (suffix: AddressSuffix) => values[addressKey(prefix, suffix)];
  const errors: FieldErrors<keyof AddressValues> = {};

  errors[addressKey(prefix, 'Name')] = validatePersonName(get('Name'), nameLabel);
  errors[addressKey(prefix, 'Phone')] = validatePhone(get('CountryCode'), get('Phone'));
  errors[addressKey(prefix, 'Line1')] = requiredText(get('Line1'), 'Address line 1', 255);
  errors[addressKey(prefix, 'Line2')] = optionalText(get('Line2'), 'Address line 2', 255);
  errors[addressKey(prefix, 'City')] = requiredText(get('City'), 'City', 100);
  errors[addressKey(prefix, 'District')] = (DISTRICTS as readonly string[]).includes(
    get('District'),
  )
    ? undefined
    : 'Select a district';
  errors[addressKey(prefix, 'PostalCode')] = optionalText(get('PostalCode'), 'Postal code', 15);
  return errors;
}

export function validateAddressStep(values: AddressValues): FieldErrors<keyof AddressValues> {
  return {
    ...addressErrors(values, 'pickup', 'Contact name'),
    ...addressErrors(values, 'dropoff', 'Recipient name'),
  };
}

// ---- Step 2: parcel ----

export type ParcelValues = {
  parcelType: string;
  weightKg: string;
  lengthCm: string;
  widthCm: string;
  heightCm: string;
  contentDescription: string;
  deliveryInstructions: string;
};

export type ParcelForm = ReturnType<typeof useForm<ParcelValues>>;

/** Maximums from System Settings (useSettings). */
export interface ParcelLimits {
  maxWeightKg: number;
  maxSideCm: number;
}

export function initialParcelValues(defaultParcelType = 'PARCEL'): ParcelValues {
  return {
    parcelType: defaultParcelType,
    weightKg: '',
    lengthCm: '',
    widthCm: '',
    heightCm: '',
    contentDescription: '',
    deliveryInstructions: '',
  };
}

function numberError(
  value: string,
  label: string,
  unit: string,
  max: number,
  maxDecimals: number,
  required: boolean,
): string | undefined {
  const text = value.trim();
  if (!text) return required ? `${label} is required` : undefined;
  if (!/^\d+(\.\d+)?$/.test(text) || Number(text) <= 0) {
    return `${label} must be a number greater than 0`;
  }
  if ((text.split('.')[1] ?? '').length > maxDecimals) {
    return `${label} can have at most ${maxDecimals} decimal places`;
  }
  if (Number(text) > max) return `${label} can be at most ${max} ${unit}`;
  return undefined;
}

export function validateParcelStep(
  values: ParcelValues,
  limits: ParcelLimits,
): FieldErrors<keyof ParcelValues> {
  const errors: FieldErrors<keyof ParcelValues> = {};

  if (!(PARCEL_TYPES as readonly string[]).includes(values.parcelType)) {
    errors.parcelType = 'Select a parcel type';
  }
  errors.weightKg = numberError(values.weightKg, 'Weight', 'kg', limits.maxWeightKg, 3, true);

  const sides = [
    ['lengthCm', 'Length'],
    ['widthCm', 'Width'],
    ['heightCm', 'Height'],
  ] as const;
  for (const [name, label] of sides) {
    errors[name] = numberError(values[name], label, 'cm', limits.maxSideCm, 2, false);
  }
  // The oversize fee needs all three sides, so they are given together or not at all.
  const filled = sides.filter(([name]) => values[name].trim() !== '');
  if (filled.length > 0 && filled.length < sides.length) {
    for (const [name] of sides) {
      if (!values[name].trim()) errors[name] = 'Enter all three sizes, or leave all empty';
    }
  }

  errors.contentDescription = optionalText(values.contentDescription, 'Contents', 500);
  errors.deliveryInstructions = optionalText(values.deliveryInstructions, 'Instructions', 500);
  return errors;
}

// ---- Request for the API ----

function emptyToUndefined(value: string): string | undefined {
  const text = value.trim();
  return text === '' ? undefined : text;
}

function toNumber(value: string): number | undefined {
  return value.trim() === '' ? undefined : Number(value);
}

function toAddress(values: AddressValues, prefix: AddressPrefix): BookingAddress {
  const get = (suffix: AddressSuffix) => values[addressKey(prefix, suffix)];
  return {
    contactName: get('Name').trim(),
    contactPhone: toE164(get('CountryCode'), get('Phone')),
    addressLine1: get('Line1').trim(),
    addressLine2: emptyToUndefined(get('Line2')),
    city: get('City').trim(),
    district: get('District'),
    postalCode: emptyToUndefined(get('PostalCode')),
  };
}

/** Builds what the estimate and booking calls send. Call it only after both steps are valid. */
export function toBookingRequest(address: AddressValues, parcel: ParcelValues): BookingRequest {
  return {
    pickup: toAddress(address, 'pickup'),
    dropoff: toAddress(address, 'dropoff'),
    parcelType: parcel.parcelType as ParcelType,
    weightKg: Number(parcel.weightKg),
    lengthCm: toNumber(parcel.lengthCm),
    widthCm: toNumber(parcel.widthCm),
    heightCm: toNumber(parcel.heightCm),
    contentDescription: emptyToUndefined(parcel.contentDescription),
    deliveryInstructions: emptyToUndefined(parcel.deliveryInstructions),
  };
}
