import type { PricingRule, PricingRuleInput } from '../../types/pricing';
import { type FieldErrors } from '../../utils/validation';

export type PricingFormValues = {
  ruleName: string;
  baseFee: string;
  includedWeightKg: string;
  additionalFeePerKg: string;
  maxVolumeCm3: string;
  oversizeFee: string;
  effectiveFrom: string; // YYYY-MM-DD, Sri Lanka time
  effectiveTo: string; // YYYY-MM-DD, Sri Lanka time, optional
};

const COLOMBO_OFFSET = '+05:30'; // Sri Lanka has no daylight saving
const MONEY_MAX = 99_999_999.99;
const DAY_FORMAT = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Colombo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function todayInColombo(): string {
  return DAY_FORMAT.format(new Date());
}

export function emptyPricingForm(): PricingFormValues {
  return {
    ruleName: '',
    baseFee: '',
    includedWeightKg: '1',
    additionalFeePerKg: '',
    maxVolumeCm3: '',
    oversizeFee: '0',
    effectiveFrom: todayInColombo(),
    effectiveTo: '',
  };
}

export function toPricingForm(rule: PricingRule): PricingFormValues {
  return {
    ruleName: rule.ruleName,
    baseFee: String(rule.baseFee),
    includedWeightKg: String(rule.includedWeightKg),
    additionalFeePerKg: String(rule.additionalFeePerKg),
    maxVolumeCm3: rule.maxVolumeCm3 == null ? '' : String(rule.maxVolumeCm3),
    oversizeFee: String(rule.oversizeFee),
    effectiveFrom: DAY_FORMAT.format(new Date(rule.effectiveFrom)),
    effectiveTo: rule.effectiveTo ? DAY_FORMAT.format(new Date(rule.effectiveTo)) : '',
  };
}

interface NumberRule {
  required: boolean;
  positive?: boolean;
  max: number;
  decimals: number;
}

function numberError(value: string, label: string, rule: NumberRule): string | undefined {
  const text = value.trim();
  if (!text) return rule.required ? `${label} is required` : undefined;
  if (!/^\d+(\.\d+)?$/.test(text)) return `${label} must be a number`;
  if ((text.split('.')[1] ?? '').length > rule.decimals) {
    return `${label} can have at most ${rule.decimals} decimal places`;
  }
  const amount = Number(text);
  if (rule.positive && amount <= 0) return `${label} must be greater than 0`;
  if (amount > rule.max) return `${label} is too large`;
  return undefined;
}

export function validatePricingForm(v: PricingFormValues): FieldErrors<keyof PricingFormValues> {
  const name = v.ruleName.trim();
  const errors: FieldErrors<keyof PricingFormValues> = {
    ruleName: !name
      ? 'Rule name is required'
      : name.length > 120
        ? 'Rule name must be at most 120 characters'
        : undefined,
    baseFee: numberError(v.baseFee, 'Base fee', { required: true, max: MONEY_MAX, decimals: 2 }),
    includedWeightKg: numberError(v.includedWeightKg, 'Included weight', {
      required: true,
      max: 99_999.999,
      decimals: 3,
    }),
    additionalFeePerKg: numberError(v.additionalFeePerKg, 'Extra fee per kg', {
      required: true,
      max: MONEY_MAX,
      decimals: 2,
    }),
    maxVolumeCm3: numberError(v.maxVolumeCm3, 'Max volume', {
      required: false,
      positive: true,
      max: 9_999_999_999.99,
      decimals: 2,
    }),
    oversizeFee: numberError(v.oversizeFee, 'Oversize fee', {
      required: true,
      max: MONEY_MAX,
      decimals: 2,
    }),
    effectiveFrom: v.effectiveFrom ? undefined : 'Start date is required',
  };
  if (v.effectiveTo && v.effectiveFrom && v.effectiveTo < v.effectiveFrom) {
    errors.effectiveTo = 'End date must be on or after the start date';
  }
  return errors;
}

export function toPricingInput(v: PricingFormValues, active: boolean): PricingRuleInput {
  return {
    ruleName: v.ruleName.trim(),
    baseFee: Number(v.baseFee),
    includedWeightKg: Number(v.includedWeightKg),
    additionalFeePerKg: Number(v.additionalFeePerKg),
    maxVolumeCm3: v.maxVolumeCm3.trim() ? Number(v.maxVolumeCm3) : null,
    oversizeFee: Number(v.oversizeFee),
    effectiveFrom: new Date(`${v.effectiveFrom}T00:00:00${COLOMBO_OFFSET}`).toISOString(),
    effectiveTo: v.effectiveTo
      ? new Date(`${v.effectiveTo}T23:59:59${COLOMBO_OFFSET}`).toISOString()
      : null,
    active,
  };
}
