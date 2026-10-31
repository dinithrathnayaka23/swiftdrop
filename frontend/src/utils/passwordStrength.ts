/**
 * Advisory password strength for the sign-up and reset forms. Acceptance is decided by
 * validateNewPassword (same rules as the backend); strength only guides the user.
 */

export type StrengthLevel = 0 | 1 | 2 | 3 | 4;

export interface PasswordStrength {
  /** 0 = nothing typed / too short, 1 = weak, 2 = fair, 3 = good, 4 = strong */
  level: StrengthLevel;
  label: string;
  /** One suggestion to make the password stronger, if any. */
  tip?: string;
}

export interface PasswordRequirement {
  label: string;
  met: boolean;
}

// Passwords people pick most often (lower-case). Anything on this list is weak whatever its shape.
const COMMON_PASSWORDS = new Set([
  'password',
  'password1',
  'password12',
  'password123',
  'passw0rd',
  '12345678',
  '123456789',
  '1234567890',
  '11111111',
  'qwerty123',
  'qwertyuiop',
  'abc12345',
  'abcd1234',
  'iloveyou1',
  'letmein1',
  'welcome1',
  'welcome123',
  'admin123',
  'srilanka1',
  'srilanka123',
  'swiftdrop1',
  'swiftdrop123',
]);

/** Mirrors validateNewPassword so the checklist and the error message never disagree. */
export function passwordRequirements(password: string): PasswordRequirement[] {
  return [
    { label: 'At least 8 characters', met: password.length >= 8 },
    { label: 'At least one letter', met: /[A-Za-z]/.test(password) },
    { label: 'At least one number', met: /\d/.test(password) },
    {
      label: 'No space at the start or end',
      met: password.length > 0 && password === password.trim(),
    },
  ];
}

export function getPasswordStrength(password: string): PasswordStrength {
  if (!password) return { level: 0, label: '' };
  if (password.length < 8) {
    return { level: 0, label: 'Too short', tip: 'Use at least 8 characters.' };
  }

  const hasLower = /[a-z]/.test(password);
  const hasUpper = /[A-Z]/.test(password);
  const hasDigit = /\d/.test(password);
  const hasSymbol = /[^A-Za-z0-9\s]/.test(password);

  let points = 1; // 8+ characters
  if (password.length >= 12) points++;
  if (hasLower && hasUpper) points++;
  if (hasDigit) points++;
  if (hasSymbol) points++;

  const common = COMMON_PASSWORDS.has(password.toLowerCase());
  const repetitive = /^(.)\1+$/.test(password) || /(.)\1{3,}/.test(password);
  if (common || repetitive) {
    return {
      level: 1,
      label: 'Weak',
      tip: common
        ? 'This is a very common password. Choose something unique.'
        : 'Avoid repeated characters.',
    };
  }

  const level = (points <= 2 ? 1 : points - 1) as StrengthLevel;
  const labels = ['', 'Weak', 'Fair', 'Good', 'Strong'] as const;

  let tip: string | undefined;
  if (level < 4) {
    if (password.length < 12) tip = 'Make it longer: 12 or more characters is much safer.';
    else if (!(hasLower && hasUpper)) tip = 'Mix upper- and lower-case letters.';
    else if (!hasSymbol) tip = 'Add a symbol such as ! @ # or $.';
    else if (!hasDigit) tip = 'Add a number.';
  }
  return { level, label: labels[level], tip };
}
