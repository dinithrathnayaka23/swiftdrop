export type AdjustmentType = 'FLAT' | 'PERCENTAGE';

export interface PricingRule {
  id: string;
  ruleName: string;
  baseFee: number;
  currency: string;
  includedWeightKg: number;
  additionalFeePerKg: number;
  maxVolumeCm3: number | null;
  oversizeFee: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  active: boolean;
  createdAt: string;
}

/** Sent when creating or updating a rule. Amounts are in LKR. */
export interface PricingRuleInput {
  ruleName: string;
  baseFee: number;
  includedWeightKg: number;
  additionalFeePerKg: number;
  maxVolumeCm3?: number | null;
  oversizeFee: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
  active: boolean;
}

export interface ZoneAdjustment {
  id: string;
  pricingRuleId: string;
  pickupZoneId: string;
  deliveryZoneId: string;
  adjustmentType: AdjustmentType;
  /** LKR for FLAT, percent of the base fee for PERCENTAGE. Negative means a discount. */
  adjustmentValue: number;
}

export interface ZoneAdjustmentInput {
  pickupZoneId: string;
  deliveryZoneId: string;
  adjustmentType: AdjustmentType;
  adjustmentValue: number;
}

export interface PricingApi {
  listRules: () => Promise<PricingRule[]>;
  createRule: (input: PricingRuleInput) => Promise<PricingRule>;
  updateRule: (ruleId: string, input: PricingRuleInput) => Promise<PricingRule>;
  /** Stops the rule being used for new fees. */
  deactivateRule: (ruleId: string) => Promise<PricingRule>;
  listAdjustments: (ruleId: string) => Promise<ZoneAdjustment[]>;
  addAdjustment: (ruleId: string, input: ZoneAdjustmentInput) => Promise<ZoneAdjustment>;
  deleteAdjustment: (ruleId: string, adjustmentId: string) => Promise<void>;
}
