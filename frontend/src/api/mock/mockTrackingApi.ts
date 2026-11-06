import type { TrackingApi, TrackingResult } from '../../types/tracking';
import { delay } from './delay';

/** Sample tracking numbers for the mock API. Anything else is "not found". */
export const MOCK_TRACKING_NUMBERS = {
  booked: 'SD246813579',
  inTransit: 'SD123456789',
  delivered: 'SD987654321',
  failed: 'SD135792468',
} as const;

/** The samples with a short label, for the demo hint on the search page. */
export const MOCK_TRACKING_SAMPLES: { trackingNumber: string; label: string }[] = [
  { trackingNumber: MOCK_TRACKING_NUMBERS.booked, label: 'booked' },
  { trackingNumber: MOCK_TRACKING_NUMBERS.inTransit, label: 'in transit' },
  { trackingNumber: MOCK_TRACKING_NUMBERS.delivered, label: 'delivered' },
  { trackingNumber: MOCK_TRACKING_NUMBERS.failed, label: 'failed' },
];

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString();
}

function sampleParcels(): Record<string, TrackingResult> {
  return {
    [MOCK_TRACKING_NUMBERS.booked]: {
      trackingNumber: MOCK_TRACKING_NUMBERS.booked,
      currentStatus: 'CREATED',
      bookedAt: hoursAgo(2),
      lastUpdatedAt: hoursAgo(2),
      fromCity: 'Nugegoda',
      toCity: 'Negombo',
      weightKg: 0.8,
      history: [{ statusCode: 'CREATED', changedAt: hoursAgo(2) }],
    },
    [MOCK_TRACKING_NUMBERS.inTransit]: {
      trackingNumber: MOCK_TRACKING_NUMBERS.inTransit,
      currentStatus: 'IN_TRANSIT',
      bookedAt: hoursAgo(54),
      lastUpdatedAt: hoursAgo(3),
      fromCity: 'Colombo 07',
      toCity: 'Kandy',
      weightKg: 2.5,
      history: [
        { statusCode: 'CREATED', changedAt: hoursAgo(54) },
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
        { statusCode: 'CREATED', changedAt: hoursAgo(80) },
        { statusCode: 'PICKED_UP', changedAt: hoursAgo(76) },
        { statusCode: 'IN_TRANSIT', changedAt: hoursAgo(54) },
        { statusCode: 'OUT_FOR_DELIVERY', changedAt: hoursAgo(9) },
        { statusCode: 'DELIVERED', changedAt: hoursAgo(5) },
      ],
    },
    [MOCK_TRACKING_NUMBERS.failed]: {
      trackingNumber: MOCK_TRACKING_NUMBERS.failed,
      currentStatus: 'FAILED',
      bookedAt: hoursAgo(30),
      lastUpdatedAt: hoursAgo(1),
      fromCity: 'Kandy',
      toCity: 'Matara',
      weightKg: 3.4,
      history: [
        { statusCode: 'CREATED', changedAt: hoursAgo(30) },
        { statusCode: 'PICKED_UP', changedAt: hoursAgo(27) },
        { statusCode: 'IN_TRANSIT', changedAt: hoursAgo(20) },
        { statusCode: 'OUT_FOR_DELIVERY', changedAt: hoursAgo(4) },
        { statusCode: 'FAILED', changedAt: hoursAgo(1) },
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
