import {
  CircleCheck,
  CircleDot,
  CircleMinus,
  CirclePause,
  CircleX,
  Clock,
  Truck,
} from 'lucide-react';
import { toApiRequestError } from '../../api/errors';
import type {
  AssignmentStatus,
  Courier,
  CourierAvailability,
  NewCourier,
} from '../../types/courier';
import { toE164 } from '../../utils/phone';
import type { Visual } from '../shared-parcels/components/statusVisuals';

/*
 * Client-side copies of the backend's courier rules (dispatch/dto/request/CourierRules and the
 * request records), so the form reports the same problems before sending. The backend still checks.
 */

export const PERSON_NAME = /^\p{L}[\p{L}\p{M}\s.'-]*$/u;
export const PHONE_E164 = /^\+[1-9]\d{6,14}$/;
export const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d)\S(.*\S)?$/;
export const VEHICLE_REGISTRATION = /^[A-Za-z0-9][A-Za-z0-9 -]*$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Common vehicle types for the picker and the filter (the backend stores free text). */
export const VEHICLE_TYPES = ['Motorbike', 'Three-wheeler', 'Car', 'Van', 'Lorry', 'Bicycle'];

export const AVAILABILITY_OPTIONS: { value: CourierAvailability; label: string }[] = [
  { value: 'ONLINE', label: 'Online' },
  { value: 'ON_BREAK', label: 'On Break' },
  { value: 'OFFLINE', label: 'Offline' },
];

const AVAILABILITY_VISUALS: Record<CourierAvailability, Visual> = {
  ONLINE: { label: 'Online', icon: CircleDot, tone: 'success' },
  ON_BREAK: { label: 'On Break', icon: CirclePause, tone: 'warning' },
  OFFLINE: { label: 'Offline', icon: CircleMinus, tone: 'danger' },
};

export function availabilityVisual(availability: CourierAvailability): Visual {
  return AVAILABILITY_VISUALS[availability];
}

const ASSIGNMENT_VISUALS: Record<AssignmentStatus, Visual> = {
  ASSIGNED: { label: 'Assigned', icon: Clock, tone: 'neutral' },
  IN_PROGRESS: { label: 'In progress', icon: Truck, tone: 'info' },
  COMPLETED: { label: 'Completed', icon: CircleCheck, tone: 'success' },
  FAILED: { label: 'Failed attempt', icon: CircleX, tone: 'danger' },
  REASSIGNED: { label: 'Reassigned', icon: CircleMinus, tone: 'warning' },
  CANCELLED: { label: 'Cancelled', icon: CircleMinus, tone: 'neutral' },
};

export function assignmentVisual(status: AssignmentStatus): Visual {
  return ASSIGNMENT_VISUALS[status];
}

/** Whether the backend lets this courier be given parcels now: active, online, account active. */
export function canTakeParcels(
  courier: Pick<Courier, 'active' | 'availability' | 'accountStatus'>,
): boolean {
  return courier.active && courier.availability === 'ONLINE' && courier.accountStatus === 'ACTIVE';
}

/** "RP" for Ramesh Perera. */
export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

// ---- Add Courier form ----------------------------------------------------------------------------

export interface CourierFormValues {
  fullName: string;
  email: string;
  countryCode: string;
  phone: string;
  password: string;
  vehicleType: string;
  vehicleRegistration: string;
  serviceAreaIds: string[];
}

export type CourierFormErrors = Partial<Record<keyof CourierFormValues | 'serviceAreaIds', string>>;

export function validateRegistration(registration: string): string | undefined {
  const value = registration.trim();
  if (!value) return 'Vehicle registration is required';
  if (value.length > 30) return 'Vehicle registration must be at most 30 characters';
  if (!VEHICLE_REGISTRATION.test(value)) {
    return 'Vehicle registration can only contain letters, numbers, spaces and hyphens';
  }
  return undefined;
}

export function validateVehicleType(vehicleType: string): string | undefined {
  const value = vehicleType.trim();
  if (!value) return 'Vehicle type is required';
  if (value.length > 50) return 'Vehicle type must be at most 50 characters';
  return undefined;
}

/** The same checks as CreateCourierRequest; an empty result means the form can be sent. */
export function validateNewCourier(values: CourierFormValues): CourierFormErrors {
  const errors: CourierFormErrors = {};
  const name = values.fullName.trim();
  if (!name) errors.fullName = 'Full name is required';
  else if (name.length < 2 || name.length > 120)
    errors.fullName = 'Full name must be 2-120 characters';
  else if (!PERSON_NAME.test(name)) {
    errors.fullName = 'Full name can only contain letters, spaces, apostrophes, dots and hyphens';
  }
  const email = values.email.trim();
  if (!email) errors.email = 'Email is required';
  else if (email.length > 255 || !EMAIL.test(email)) errors.email = 'Enter a valid email address';
  if (!values.phone.trim()) errors.phone = 'Phone number is required';
  else if (!PHONE_E164.test(toE164(values.countryCode, values.phone))) {
    errors.phone = 'Enter a valid phone number, e.g. 77 123 4567';
  }
  if (!values.password) errors.password = 'Password is required';
  else if (values.password.length < 8 || values.password.length > 72) {
    errors.password = 'Password must be 8-72 characters';
  } else if (!PASSWORD.test(values.password)) {
    errors.password =
      'Password must include at least one letter and one number and cannot start or end with a space';
  }
  errors.vehicleType = validateVehicleType(values.vehicleType);
  errors.vehicleRegistration = validateRegistration(values.vehicleRegistration);
  return Object.fromEntries(
    Object.entries(errors).filter(([, message]) => message),
  ) as CourierFormErrors;
}

export function toNewCourier(values: CourierFormValues): NewCourier {
  return {
    fullName: values.fullName.trim(),
    email: values.email.trim(),
    phone: toE164(values.countryCode, values.phone),
    password: values.password,
    vehicleType: values.vehicleType.trim(),
    vehicleRegistration: values.vehicleRegistration.trim(),
    serviceAreaIds: values.serviceAreaIds,
  };
}

// ---- Errors from the API ---------------------------------------------------------------------------

const UNKNOWN_SERVICE_AREA = 'Unknown or inactive service area';

/**
 * Field errors (400 validation, 409 email/registration) by field, plus the message to show above the
 * form when nothing is field-specific. The backend's 400 for an unknown or inactive service area has
 * no field: it belongs to the service-area picker.
 */
export function courierErrorsFrom(err: unknown): { fields: CourierFormErrors; message?: string } {
  const error = toApiRequestError(err);
  const fields: CourierFormErrors = {};
  for (const { field, message } of error.fieldErrors) {
    fields[field as keyof CourierFormErrors] ??= message;
  }
  if (error.message === UNKNOWN_SERVICE_AREA) {
    fields.serviceAreaIds = 'One of these service areas is unknown or no longer active';
  }
  return Object.keys(fields).length > 0 ? { fields } : { fields, message: error.message };
}
