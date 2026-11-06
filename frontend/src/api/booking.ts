import type {
  BookingApi,
  BookingConfirmation,
  BookingRequest,
  FeeEstimate,
} from '../types/booking';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { request } from './errors';
import { mockBookingApi } from './mock/mockBookingApi';

const httpBookingApi: BookingApi = {
  estimate: (booking: BookingRequest) =>
    request(() => apiClient.post<FeeEstimate>('/bookings/estimate', booking)),

  book: (booking: BookingRequest) =>
    request(() => apiClient.post<BookingConfirmation>('/bookings', booking)),
};

/** Book a Delivery screens. Uses sample data until the backend is switched on. */
export const bookingApi: BookingApi = isMockApiEnabled() ? mockBookingApi : httpBookingApi;
