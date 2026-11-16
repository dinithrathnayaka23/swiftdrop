import { apiClient } from '../../api/client';
import { request } from '../../api/errors';
import type { DailyDeliveryStat, DateRange, ReportSummary } from './types';

/** Operational reports (DISPATCHER_ADMIN only). 400 for an invalid range, with the reason. */
export const reportsApi = {
  summary: (range: DateRange) =>
    request(() => apiClient.get<ReportSummary>('/admin/reports/summary', { params: range })),

  /** One entry per day in the range, oldest first, including days with no deliveries. */
  deliveriesPerDay: (range: DateRange) =>
    request(() =>
      apiClient.get<DailyDeliveryStat[]>('/admin/reports/deliveries-per-day', { params: range }),
    ),
};
