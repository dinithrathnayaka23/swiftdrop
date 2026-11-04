import type {
  AdminParcelCounts,
  AdminParcelDetail,
  AdminParcelRow,
  CustomerParcelDetail,
  CustomerParcelSummary,
  Paged,
  ParcelsApi,
  StatusCounts,
} from '../types/parcel';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { ApiRequestError, request } from './errors';
import { mockParcelsApi } from './mock/mockParcelsApi';

/** Treats 404 as "not found" (null) so pages can show their not-found state. */
async function orNull<T>(call: () => Promise<T>): Promise<T | null> {
  try {
    return await call();
  } catch (err) {
    if (err instanceof ApiRequestError && err.status === 404) return null;
    throw err;
  }
}

const httpParcelsApi: ParcelsApi = {
  getMyCounts: () => request(() => apiClient.get<StatusCounts>('/customer/parcels/counts')),

  listMyParcels: (filters) =>
    request(() =>
      apiClient.get<Paged<CustomerParcelSummary>>('/customer/parcels', { params: filters }),
    ),

  getMyParcel: (trackingNumber) =>
    orNull(() =>
      request(() =>
        apiClient.get<CustomerParcelDetail>(
          `/customer/parcels/by-tracking-number/${encodeURIComponent(trackingNumber)}`,
        ),
      ),
    ),

  downloadReceipt: (parcelId) =>
    request(() =>
      apiClient.get<Blob>(`/customer/parcels/${encodeURIComponent(parcelId)}/receipt`, {
        responseType: 'blob',
      }),
    ),

  getAdminCounts: () => request(() => apiClient.get<AdminParcelCounts>('/admin/parcels/counts')),

  listAdminParcels: (filters) =>
    request(() => apiClient.get<Paged<AdminParcelRow>>('/admin/parcels', { params: filters })),

  getAdminParcel: (parcelId) =>
    orNull(() =>
      request(() =>
        apiClient.get<AdminParcelDetail>(`/admin/parcels/${encodeURIComponent(parcelId)}`),
      ),
    ),
};

/** Customer and admin parcel screens. Uses sample data until the parcel endpoints are built. */
export const parcelsApi: ParcelsApi = isMockApiEnabled() ? mockParcelsApi : httpParcelsApi;
