/**
 * Client-side validation rules, shared by every form. They give instant feedback; the backend
 * validates everything again. Length limits match the database columns (V1__init.sql).
 */
import { countryFor, nationalDigits } from './phone';

export type FieldErrors<K extends string> = Partial<Record<K, string>>;

export function hasErrors(errors: Record<string, string | undefined>): boolean {
  return Object.values(errors).some(Boolean);
}

type Rule = (value: string) => string | undefined;

const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
// Letters from any language, plus spaces, apostrophes, dots and hyphens ("Mary-Ann O'Neil Jr.").
const PERSON_NAME_PATTERN = /^\p{L}[\p{L}\p{M}\s.'-]*$/u;
// Sri Lankan company registration (Companies Act No. 7 of 2007): PV (private), PB (public) or
// GA (guarantee) followed by 8 digits, e.g. PV00234567. Checked after normalising.
const REGISTRATION_NO_PATTERN = /^(PV|PB|GA)\d{8}$/;
const TRACKING_NUMBER_PATTERN = /^[A-Za-z0-9-]+$/;

function lengthRule(label: string, min: number, max: number): Rule {
  return (value) => {
    const length = value.trim().length;
    if (length < min) return `${label} must be at least ${min} characters`;
    if (length > max) return `${label} must be at most ${max} characters`;
    return undefined;
  };
}

/** Runs rules in order; "required" is checked first. */
function check(value: string, label: string, ...rules: Rule[]): string | undefined {
  if (!value.trim()) return `${label} is required`;
  for (const rule of rules) {
    const error = rule(value);
    if (error) return error;
  }
  return undefined;
}

// ---- individual fields ----

export function validateEmail(value: string): string | undefined {
  return check(
    value,
    'Email',
    (v) => (v.trim().length > 255 ? 'Email must be at most 255 characters' : undefined),
    (v) => (EMAIL_PATTERN.test(v.trim()) ? undefined : 'Enter a valid email address'),
  );
}

export function validatePersonName(value: string, label = 'Full name'): string | undefined {
  return check(value, label, lengthRule(label, 2, 120), (v) =>
    PERSON_NAME_PATTERN.test(v.trim())
      ? undefined
      : `${label} can only contain letters, spaces, apostrophes, dots and hyphens`,
  );
}

export function validatePasswordConfirmation(
  password: string,
  confirmation: string,
): string | undefined {
  if (!confirmation) return 'Please confirm your new password';
  return confirmation === password ? undefined : 'Passwords do not match';
}

/** Sign-in: only presence is checked, so existing passwords are never rejected client-side. */
export function validateLoginPassword(value: string): string | undefined {
  return value ? undefined : 'Password is required';
}

/** New passwords. BCrypt only uses the first 72 bytes, so cap the length. */
export function validateNewPassword(value: string): string | undefined {
  if (!value) return 'Password is required';
  if (value.length < 8) return 'Password must be at least 8 characters';
  if (value.length > 72) return 'Password must be at most 72 characters';
  if (value !== value.trim()) return 'Password cannot start or end with a space';
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return 'Password must include at least one letter and one number';
  }
  return undefined;
}

export function validatePhone(countryCode: string, number: string): string | undefined {
  const compact = number.replace(/[\s()-]/g, '');
  if (!compact) return 'Phone number is required';
  if (!/^\d+$/.test(compact)) return 'Phone number can only contain digits';
  const country = countryFor(countryCode);
  if (!country) return 'Select a country code';
  if (nationalDigits(number).length !== country.digits) {
    return `Enter a valid ${country.country} number (${country.digits} digits, e.g. ${country.example})`;
  }
  return undefined;
}

export function validateBusinessName(value: string): string | undefined {
  return check(value, 'Business name', lengthRule('Business name', 2, 180));
}

/**
 * Standard form of a registration number: upper case, without spaces or hyphens.
 * "pv 0023-4567" → "PV00234567". The backend applies the same normalisation.
 */
export function normalizeRegistrationNo(value: string): string {
  return value.replace(/[\s-]/g, '').toUpperCase();
}

export function validateRegistrationNo(value: string): string | undefined {
  return check(value, 'Registration number', (v) =>
    REGISTRATION_NO_PATTERN.test(normalizeRegistrationNo(v))
      ? undefined
      : 'Enter a valid registration number: PV, PB or GA followed by 8 digits (e.g. PV00234567)',
  );
}

export function validateAddress(value: string): string | undefined {
  return check(
    value,
    'Business address',
    (v) => (v.trim().length < 10 ? 'Please enter the full business address' : undefined),
    (v) => (v.trim().length > 500 ? 'Business address must be at most 500 characters' : undefined),
  );
}

export function validateTrackingNumber(value: string): string | undefined {
  const trimmed = value.trim();
  if (!trimmed) return 'Please enter a tracking number';
  if (!TRACKING_NUMBER_PATTERN.test(trimmed)) {
    return 'Tracking numbers contain only letters, numbers and hyphens';
  }
  if (trimmed.length < 6 || trimmed.length > 40) return 'Enter a valid tracking number';
  return undefined;
}

// ---- whole forms ----

export interface LoginValues {
  email: string;
  password: string;
}

export function validateLoginForm(v: LoginValues): FieldErrors<keyof LoginValues> {
  return { email: validateEmail(v.email), password: validateLoginPassword(v.password) };
}

export interface CustomerSignupValues {
  fullName: string;
  email: string;
  countryCode: string;
  phone: string;
  password: string;
}

export function validateCustomerSignupForm(
  v: CustomerSignupValues,
): FieldErrors<keyof CustomerSignupValues> {
  return {
    fullName: validatePersonName(v.fullName),
    email: validateEmail(v.email),
    phone: validatePhone(v.countryCode, v.phone),
    password: validateNewPassword(v.password),
  };
}

export interface MerchantSignupValues {
  businessName: string;
  registrationNo: string;
  contactPersonName: string;
  email: string;
  countryCode: string;
  phone: string;
  businessAddress: string;
  password: string;
}

export function validateMerchantSignupForm(
  v: MerchantSignupValues,
): FieldErrors<keyof MerchantSignupValues> {
  return {
    businessName: validateBusinessName(v.businessName),
    registrationNo: validateRegistrationNo(v.registrationNo),
    contactPersonName: validatePersonName(v.contactPersonName, 'Contact person name'),
    email: validateEmail(v.email),
    phone: validatePhone(v.countryCode, v.phone),
    businessAddress: validateAddress(v.businessAddress),
    password: validateNewPassword(v.password),
  };
}
