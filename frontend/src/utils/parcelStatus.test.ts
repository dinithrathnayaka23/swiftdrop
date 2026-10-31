import type { TrackingResult } from '../types/tracking';
import { buildTimeline } from './parcelStatus';

function result(overrides: Partial<TrackingResult>): TrackingResult {
  return {
    trackingNumber: 'SD1',
    currentStatus: 'BOOKED',
    lastUpdatedAt: '2026-01-02T10:00:00Z',
    bookedAt: '2026-01-01T10:00:00Z',
    fromCity: 'Colombo',
    toCity: 'Kandy',
    weightKg: 1,
    history: [],
    ...overrides,
  };
}

describe('buildTimeline', () => {
  it('marks reached steps complete and the latest one current', () => {
    const steps = buildTimeline(
      result({
        currentStatus: 'IN_TRANSIT',
        history: [
          { statusCode: 'PICKED_UP', changedAt: '2026-01-01T12:00:00Z' },
          { statusCode: 'IN_TRANSIT', changedAt: '2026-01-02T10:00:00Z' },
        ],
      }),
    );
    expect(steps.map((s) => s.state)).toEqual(['complete', 'current', 'upcoming', 'upcoming']);
  });

  it('marks every step complete once delivered', () => {
    const steps = buildTimeline(
      result({
        currentStatus: 'DELIVERED',
        history: ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'].map((code) => ({
          statusCode: code as TrackingResult['currentStatus'],
          changedAt: '2026-01-02T10:00:00Z',
        })),
      }),
    );
    expect(steps.every((s) => s.state === 'complete')).toBe(true);
  });

  it('shows nothing reached before pickup', () => {
    const steps = buildTimeline(result({ currentStatus: 'BOOKED' }));
    expect(steps.every((s) => s.state === 'upcoming')).toBe(true);
  });
});
