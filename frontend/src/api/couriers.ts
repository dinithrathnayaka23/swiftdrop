import type { Courier, CourierAssignmentRow, CourierSummary, CouriersApi } from '../types/courier';
import type { Paged } from '../types/parcel';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { request } from './errors';
import { mockCouriersApi } from './mock/mockCouriersApi';

const BASE = '/admin/couriers';

function path(courierId: string, suffix = ''): string {
  return `${BASE}/${encodeURIComponent(courierId)}${suffix}`;
}

/** The admin Couriers API (dispatch module, DISPATCHER_ADMIN). See docs/dispatch.md. */
export const httpCouriersApi: CouriersApi = {
  list: (filters) => request(() => apiClient.get<Paged<Courier>>(BASE, { params: filters })),

  summary: () => request(() => apiClient.get<CourierSummary>(`${BASE}/summary`)),

  get: (courierId) => request(() => apiClient.get<Courier>(path(courierId))),

  create: (courier) => request(() => apiClient.post<Courier>(BASE, courier)),

  update: (courierId, update) => request(() => apiClient.patch<Courier>(path(courierId), update)),

  changeAvailability: (courierId, availability) =>
    request(() => apiClient.patch<Courier>(path(courierId, '/availability'), { availability })),

  listAssignments: (courierId, query) =>
    request(() =>
      apiClient.get<Paged<CourierAssignmentRow>>(path(courierId, '/assignments'), {
        params: query,
      }),
    ),
};

export const couriersApi: CouriersApi = isMockApiEnabled() ? mockCouriersApi : httpCouriersApi;
