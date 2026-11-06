import type {
  PricingApi,
  PricingRule,
  PricingRuleInput,
  ZoneAdjustment,
  ZoneAdjustmentInput,
} from '../types/pricing';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { request } from './errors';
import { mockPricingApi } from './mock/mockPricingApi';

const httpPricingApi: PricingApi = {
  listRules: () => request(() => apiClient.get<PricingRule[]>('/pricing-rules')),

  createRule: (input: PricingRuleInput) =>
    request(() => apiClient.post<PricingRule>('/pricing-rules', input)),

  updateRule: (ruleId: string, input: PricingRuleInput) =>
    request(() =>
      apiClient.put<PricingRule>(`/pricing-rules/${encodeURIComponent(ruleId)}`, input),
    ),

  deactivateRule: (ruleId: string) =>
    request(() =>
      apiClient.post<PricingRule>(`/pricing-rules/${encodeURIComponent(ruleId)}/deactivate`),
    ),

  listAdjustments: (ruleId: string) =>
    request(() =>
      apiClient.get<ZoneAdjustment[]>(
        `/pricing-rules/${encodeURIComponent(ruleId)}/zone-adjustments`,
      ),
    ),

  addAdjustment: (ruleId: string, input: ZoneAdjustmentInput) =>
    request(() =>
      apiClient.post<ZoneAdjustment>(
        `/pricing-rules/${encodeURIComponent(ruleId)}/zone-adjustments`,
        input,
      ),
    ),

  deleteAdjustment: (ruleId: string, adjustmentId: string) =>
    request<void>(() =>
      apiClient.delete(
        `/pricing-rules/${encodeURIComponent(ruleId)}/zone-adjustments/${encodeURIComponent(adjustmentId)}`,
      ),
    ),
};

/** Admin Pricing Rules screen. Uses sample data until the backend is switched on. */
export const pricingApi: PricingApi = isMockApiEnabled() ? mockPricingApi : httpPricingApi;
