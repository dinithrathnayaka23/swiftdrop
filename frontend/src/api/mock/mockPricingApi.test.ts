import { ApiRequestError } from '../errors';
import { mockPricingApi } from './mockPricingApi';
import type { PricingRuleInput } from '../../types/pricing';

const input: PricingRuleInput = {
  ruleName: '  Express  ',
  baseFee: 500,
  includedWeightKg: 1,
  additionalFeePerKg: 150,
  oversizeFee: 250,
  effectiveFrom: '2026-02-01T00:00:00Z',
  active: true,
};

describe('mockPricingApi', () => {
  it('starts with one active rule', async () => {
    const rules = await mockPricingApi.listRules();

    expect(rules.some((r) => r.active)).toBe(true);
  });

  it('creates a rule with a trimmed name', async () => {
    const rule = await mockPricingApi.createRule(input);

    expect(rule.ruleName).toBe('Express');
    expect(rule.currency).toBe('LKR');
  });

  it('rejects an end date before the start date', async () => {
    await expect(
      mockPricingApi.createRule({ ...input, effectiveTo: '2026-01-01T00:00:00Z' }),
    ).rejects.toMatchObject({ status: 400 });
  });

  it('deactivates a rule', async () => {
    const rule = await mockPricingApi.createRule(input);

    const result = await mockPricingApi.deactivateRule(rule.id);

    expect(result.active).toBe(false);
  });

  it('refuses a second adjustment for the same route', async () => {
    const rule = await mockPricingApi.createRule(input);
    const adjustment = {
      pickupZoneId: 'zone-a',
      deliveryZoneId: 'zone-b',
      adjustmentType: 'FLAT' as const,
      adjustmentValue: 100,
    };
    await mockPricingApi.addAdjustment(rule.id, adjustment);

    const error = await mockPricingApi.addAdjustment(rule.id, adjustment).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(409);
  });
});
