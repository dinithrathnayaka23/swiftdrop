import type { BookingAddress, BookingRequest } from '../../types/booking';
import { mockBookingApi } from './mockBookingApi';

const address: BookingAddress = {
  contactName: 'Kamala Silva',
  contactPhone: '0771234567',
  addressLine1: '12 Main Street',
  city: 'Kandy',
  district: 'Kandy',
};

function booking(extra: Partial<BookingRequest> = {}): BookingRequest {
  return { pickup: address, dropoff: address, parcelType: 'PARCEL', weightKg: 1, ...extra };
}

describe('mockBookingApi', () => {
  it('charges only the base fee for a light parcel', async () => {
    const estimate = await mockBookingApi.estimate(booking());

    expect(estimate.totalFee).toBe(350);
    expect(estimate.lines).toHaveLength(1);
  });

  it('rounds extra weight up to whole kilograms', async () => {
    const estimate = await mockBookingApi.estimate(booking({ weightKg: 3.2 }));

    expect(estimate.totalFee).toBe(650);
  });

  it('adds the oversize fee when the parcel is too big', async () => {
    const estimate = await mockBookingApi.estimate(
      booking({ lengthCm: 50, widthCm: 40, heightCm: 30 }),
    );

    expect(estimate.totalFee).toBe(550);
  });

  it('returns a tracking number when booking', async () => {
    const confirmation = await mockBookingApi.book(booking());

    expect(confirmation.trackingNumber).toMatch(/^SD/);
    expect(confirmation.status).toBe('CREATED');
  });
});
