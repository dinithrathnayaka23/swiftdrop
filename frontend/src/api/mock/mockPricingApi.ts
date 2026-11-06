import type { PricingApi, PricingRule, ZoneAdjustment } from '../../types/pricing';
import { ApiRequestError } from '../errors';

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), 300));
}

let rules: PricingRule[] = [
  {
    id: 'rule-standard',
    ruleName: 'Standard',
    baseFee: 350,
    currency: 'LKR',
    includedWeightKg: 1,
    additionalFeePerKg: 100,
    maxVolumeCm3: 50_000,
    oversizeFee: 200,
    effectiveFrom: '2026-01-01T00:00:00Z',
    effectiveTo: null,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
  },
];
let adjustments: ZoneAdjustment[] = [];

function findRule(ruleId: string): PricingRule {
  const rule = rules.find((r) => r.id === ruleId);
  if (!rule) throw new ApiRequestError('Pricing rule not found', { status: 404 });
  return rule;
}

function checkDates(from: string, to?: string | null) {
  if (to && new Date(to) <= new Date(from)) {
    throw new ApiRequestError('The end date must be after the start date', { status: 400 });
  }
}

export const mockPricingApi: PricingApi = {
  listRules: () => delay([...rules]),

  createRule: async (input) => {
    checkDates(input.effectiveFrom, input.effectiveTo);
    const rule: PricingRule = {
      ...input,
      ruleName: input.ruleName.trim(),
      id: crypto.randomUUID(),
      currency: 'LKR',
      maxVolumeCm3: input.maxVolumeCm3 ?? null,
      effectiveTo: input.effectiveTo ?? null,
      createdAt: new Date().toISOString(),
    };
    rules = [rule, ...rules];
    return delay(rule);
  },

  updateRule: async (ruleId, input) => {
    const current = findRule(ruleId);
    checkDates(input.effectiveFrom, input.effectiveTo);
    const updated: PricingRule = {
      ...current,
      ...input,
      ruleName: input.ruleName.trim(),
      maxVolumeCm3: input.maxVolumeCm3 ?? null,
      effectiveTo: input.effectiveTo ?? null,
    };
    rules = rules.map((r) => (r.id === ruleId ? updated : r));
    return delay(updated);
  },

  deactivateRule: async (ruleId) => {
    const updated = { ...findRule(ruleId), active: false };
    rules = rules.map((r) => (r.id === ruleId ? updated : r));
    return delay(updated);
  },

  listAdjustments: async (ruleId) => {
    findRule(ruleId);
    return delay(adjustments.filter((a) => a.pricingRuleId === ruleId));
  },

  addAdjustment: async (ruleId, input) => {
    findRule(ruleId);
    const exists = adjustments.some(
      (a) =>
        a.pricingRuleId === ruleId &&
        a.pickupZoneId === input.pickupZoneId &&
        a.deliveryZoneId === input.deliveryZoneId,
    );
    if (exists) throw new ApiRequestError('This route already has an adjustment', { status: 409 });
    const adjustment: ZoneAdjustment = { ...input, id: crypto.randomUUID(), pricingRuleId: ruleId };
    adjustments = [...adjustments, adjustment];
    return delay(adjustment);
  },

  deleteAdjustment: async (ruleId, adjustmentId) => {
    adjustments = adjustments.filter((a) => !(a.id === adjustmentId && a.pricingRuleId === ruleId));
    return delay(undefined);
  },
};
