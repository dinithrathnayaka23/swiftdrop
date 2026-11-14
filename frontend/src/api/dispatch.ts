import type { Paged } from '../types/parcel';
import type {
  ActiveDelivery,
  Assignment,
  BulkAssignResult,
  DispatchApi,
  FailedDecision,
  FailedDelivery,
  Overview,
} from '../types/dispatch';
import type { FailedDeliveryAction } from '../types/settings';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { request } from './errors';
import { mockDispatchApi } from './mock/mockDispatchApi';

const BASE = '/admin/dispatch';

type ParcelAction = 'reassign' | 'unassign' | 'retry' | 'close';

function parcelPath(parcelId: string, action: ParcelAction): string {
  return `${BASE}/parcels/${encodeURIComponent(parcelId)}/${action}`;
}

/**
 * Assignments (AdminDispatchController), failed deliveries (AdminFailedDeliveryController) and the
 * Operations Overview (AdminOperationsController). See docs/dispatch.md.
 */
export const httpDispatchApi: DispatchApi = {
  assign: (parcelId, courierId) =>
    request(() => apiClient.post<Assignment>(`${BASE}/assignments`, { parcelId, courierId })),

  assignAll: (courierId, parcelIds) =>
    request(() =>
      apiClient.post<BulkAssignResult>(`${BASE}/assignments/bulk`, { courierId, parcelIds }),
    ),

  reassign: (parcelId, courierId, reason) =>
    request(() =>
      apiClient.post<Assignment>(parcelPath(parcelId, 'reassign'), { courierId, reason }),
    ),

  unassign: (parcelId, reason) =>
    request(() =>
      apiClient.post<Assignment>(parcelPath(parcelId, 'unassign'), reason ? { reason } : {}),
    ),

  getAssignment: (assignmentId) =>
    request(() =>
      apiClient.get<Assignment>(`${BASE}/assignments/${encodeURIComponent(assignmentId)}`),
    ),

  listFailed: (query) =>
    request(() =>
      apiClient.get<Paged<FailedDelivery>>(`${BASE}/failed-deliveries`, { params: query }),
    ),

  defaultFailedAction: async () =>
    (
      await request(() =>
        apiClient.get<{ action: FailedDeliveryAction }>(`${BASE}/failed-deliveries/default-action`),
      )
    ).action,

  retry: (parcelId, note) =>
    request(() =>
      apiClient.post<FailedDecision>(parcelPath(parcelId, 'retry'), note ? { note } : {}),
    ),

  close: (parcelId, reason) =>
    request(() => apiClient.post<FailedDecision>(parcelPath(parcelId, 'close'), { reason })),

  overview: () => request(() => apiClient.get<Overview>(`${BASE}/overview`)),

  activeDeliveries: (query) =>
    request(() =>
      apiClient.get<Paged<ActiveDelivery>>(`${BASE}/active-deliveries`, { params: query }),
    ),
};

export const dispatchApi: DispatchApi = isMockApiEnabled() ? mockDispatchApi : httpDispatchApi;
