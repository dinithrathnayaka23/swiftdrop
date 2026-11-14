import { apiClient } from '../../api/client';
import { ApiRequestError, request } from '../../api/errors';
import { isMockApiEnabled } from '../../utils/env';
import { mockCourierApi } from './mock/mockCourierApi';
import type {
  CourierApi,
  CourierDeliveryDetail,
  DeliverRequest,
  FailureReason,
  TodaysDeliveries,
} from './types';

/** Treats 404 as "not one of your deliveries" (null) so pages can show their not-found state. */
async function orNull<T>(call: () => Promise<T>): Promise<T | null> {
  try {
    return await call();
  } catch (err) {
    if (err instanceof ApiRequestError && err.status === 404) return null;
    throw err;
  }
}

/** Removes every space, as a scanned or typed label may contain them. */
export function compactTrackingNumber(trackingNumber: string): string {
  return trackingNumber.replace(/\s+/g, '');
}

const httpCourierApi: CourierApi = {
  getToday: () => request(() => apiClient.get<TodaysDeliveries>('/courier/deliveries/today')),

  getDelivery: (parcelId) =>
    orNull(() =>
      request(() =>
        apiClient.get<CourierDeliveryDetail>(`/courier/deliveries/${encodeURIComponent(parcelId)}`),
      ),
    ),

  findByTrackingNumber: async (trackingNumber) => {
    const compact = compactTrackingNumber(trackingNumber);
    if (!compact) return null;
    return orNull(() =>
      request(() =>
        apiClient.get<CourierDeliveryDetail>(
          `/courier/deliveries/by-tracking-number/${encodeURIComponent(compact)}`,
        ),
      ),
    );
  },

  updateStatus: (parcelId, { status, note }) =>
    request(() =>
      apiClient.post<CourierDeliveryDetail>(
        `/courier/deliveries/${encodeURIComponent(parcelId)}/status`,
        { status, note: note?.trim() || undefined },
      ),
    ),

  getFailureReasons: () =>
    request(() => apiClient.get<FailureReason[]>('/courier/failure-reasons')),

  reportFailure: (parcelId, { reasonCode, note }) =>
    request(() =>
      apiClient.post<CourierDeliveryDetail>(
        `/courier/deliveries/${encodeURIComponent(parcelId)}/fail`,
        { reasonCode, note: note?.trim() || undefined },
      ),
    ),

  deliver: (parcelId, delivery) =>
    request(() =>
      apiClient.post<CourierDeliveryDetail>(
        `/courier/deliveries/${encodeURIComponent(parcelId)}/deliver`,
        deliveryForm(delivery),
        // The client sends JSON by default, and axios would turn FormData into JSON with that
        // header; this lets the browser set multipart/form-data with its boundary.
        { headers: { 'Content-Type': 'multipart/form-data' } },
      ),
    ),
};

/** The multipart body of POST /deliver. */
export function deliveryForm({ recipientName, note, photo }: DeliverRequest): FormData {
  const form = new FormData();
  form.append('recipientName', recipientName.trim());
  const trimmedNote = note?.trim();
  if (trimmedNote) form.append('note', trimmedNote);
  if (photo) form.append('photo', photo, 'proof.jpg');
  return form;
}

/** The courier app's deliveries. Sample data when the mock API is on (VITE_USE_MOCK_API). */
export const courierApi: CourierApi = isMockApiEnabled() ? mockCourierApi : httpCourierApi;
