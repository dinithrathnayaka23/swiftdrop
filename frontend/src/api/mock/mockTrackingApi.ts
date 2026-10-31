import type { TrackingApi, TrackingResult } from '../../types/tracking';
import { delay } from './delay';

/** Sample tracking numbers for the mock API. Anything else is "not found". */
export const MOCK_TRACKING_NUMBERS = {
  inTransit: 'SD123456789',
  delivered: 'SD987654321',
} as const;

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString();
}

function sampleParcels(): Record<string, TrackingResult> {
  return {
    [MOCK_TRACKING_NUMBERS.inTransit]: {
      trackingNumber: MOCK_TRACKING_NUMBERS.inTransit,
      currentStatus: 'IN_TRANSIT',
      bookedAt: hoursAgo(54),
      lastUpdatedAt: hoursAgo(3),
      fromCity: 'Colombo 07',
      toCity: 'Kandy',
      weightKg: 2.5,
      history: [
        { statusCode: 'BOOKED', changedAt: hoursAgo(54) },
        { statusCode: 'ASSIGNED', changedAt: hoursAgo(52) },
        { statusCode: 'PICKED_UP', changedAt: hoursAgo(50) },
        { statusCode: 'IN_TRANSIT', changedAt: hoursAgo(3) },
      ],
    },
    [MOCK_TRACKING_NUMBERS.delivered]: {
      trackingNumber: MOCK_TRACKING_NUMBERS.delivered,
      currentStatus: 'DELIVERED',
      bookedAt: hoursAgo(80),
      lastUpdatedAt: hoursAgo(5),
      fromCity: 'Colombo 05',
      toCity: 'Galle',
      weightKg: 1.2,
      history: [
        { statusCode: 'BOOKED', changedAt: hoursAgo(80) },
        { statusCode: 'ASSIGNED', changedAt: hoursAgo(78) },
        { statusCode: 'PICKED_UP', changedAt: hoursAgo(76) },
        { statusCode: 'IN_TRANSIT', changedAt: hoursAgo(54) },
        { statusCode: 'OUT_FOR_DELIVERY', changedAt: hoursAgo(9) },
        { statusCode: 'DELIVERED', changedAt: hoursAgo(5) },
      ],
    },
  };
}

export const mockTrackingApi: TrackingApi = {
  async getTracking(trackingNumber) {
    await delay(600);
    return sampleParcels()[trackingNumber.toUpperCase()] ?? null;
  },
};
