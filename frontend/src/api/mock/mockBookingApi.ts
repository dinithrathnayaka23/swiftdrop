import type { BookingApi, BookingRequest, FeeEstimate, FeeLine } from '../../types/booking';

const BASE_FEE = 350;
const INCLUDED_KG = 1;
const FEE_PER_KG = 100;
const MAX_VOLUME_CM3 = 50_000;
const OVERSIZE_FEE = 200;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), 300));
}

function calculate(booking: BookingRequest): FeeEstimate {
  const lines: FeeLine[] = [{ label: 'Base fee', amount: BASE_FEE }];

  const extraKg = Math.ceil(booking.weightKg - INCLUDED_KG);
  if (extraKg > 0) {
    lines.push({ label: `Extra weight (${extraKg} kg)`, amount: extraKg * FEE_PER_KG });
  }

  const { lengthCm, widthCm, heightCm } = booking;
  if (lengthCm && widthCm && heightCm && lengthCm * widthCm * heightCm > MAX_VOLUME_CM3) {
    lines.push({ label: 'Oversize fee', amount: OVERSIZE_FEE });
  }

  const totalFee = lines.reduce((sum, line) => sum + line.amount, 0);
  return { totalFee, currency: 'LKR', lines };
}

export const mockBookingApi: BookingApi = {
  estimate: (booking) => delay(calculate(booking)),

  book: async (booking) => {
    const { totalFee, currency } = calculate(booking);
    return delay({
      parcelId: crypto.randomUUID(),
      trackingNumber: `SD${Math.random().toString(36).slice(2, 12).toUpperCase()}`,
      status: 'CREATED',
      totalFee,
      currency,
      bookedAt: new Date().toISOString(),
    });
  },
};
