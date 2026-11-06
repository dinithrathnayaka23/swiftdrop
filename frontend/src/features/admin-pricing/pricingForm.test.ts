import { hasErrors } from '../../utils/validation';
import {
  emptyPricingForm,
  toPricingInput,
  validatePricingForm,
  type PricingFormValues,
} from './pricingForm';

const valid: PricingFormValues = {
  ...emptyPricingForm(),
  ruleName: 'Express',
  baseFee: '500',
  additionalFeePerKg: '150',
  effectiveFrom: '2026-02-01',
};

describe('validatePricingForm', () => {
  it('accepts a complete rule', () => {
    expect(hasErrors(validatePricingForm(valid))).toBe(false);
  });

  it('requires a name and a base fee', () => {
    const errors = validatePricingForm({ ...valid, ruleName: ' ', baseFee: '' });

    expect(errors.ruleName).toBeDefined();
    expect(errors.baseFee).toBeDefined();
  });

  it('refuses more than two decimals in money', () => {
    expect(validatePricingForm({ ...valid, baseFee: '350.123' }).baseFee).toContain('2 decimal');
  });

  it('refuses a negative amount', () => {
    expect(validatePricingForm({ ...valid, oversizeFee: '-5' }).oversizeFee).toBeDefined();
  });

  it('refuses an end date before the start date', () => {
    const errors = validatePricingForm({ ...valid, effectiveTo: '2026-01-15' });

    expect(errors.effectiveTo).toBeDefined();
  });
});

describe('toPricingInput', () => {
  it('sends Sri Lanka midnight as UTC and leaves the empty optional fields null', () => {
    const input = toPricingInput(valid, true);

    expect(input.effectiveFrom).toBe('2026-01-31T18:30:00.000Z');
    expect(input.effectiveTo).toBeNull();
    expect(input.maxVolumeCm3).toBeNull();
    expect(input.ruleName).toBe('Express');
    expect(input.active).toBe(true);
  });
});
