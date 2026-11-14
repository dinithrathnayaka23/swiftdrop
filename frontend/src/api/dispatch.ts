import type { Assignment, BulkAssignResult, DispatchApi } from '../types/dispatch';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { request } from './errors';
import { mockDispatchApi } from './mock/mockDispatchApi';

const BASE = '/admin/dispatch';

function parcelPath(parcelId: string, action: 'reassign' | 'unassign'): string {
  return `${BASE}/parcels/${encodeURIComponent(parcelId)}/${action}`;
}

/** Assign, bulk assign, reassign and unassign (AdminDispatchController). See docs/dispatch.md. */
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
};

export const dispatchApi: DispatchApi = isMockApiEnabled() ? mockDispatchApi : httpDispatchApi;
