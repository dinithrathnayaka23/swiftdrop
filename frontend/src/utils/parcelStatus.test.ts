import type { TrackingResult } from '../types/tracking';
import {
  buildTimeline,
  isFailureStatus,
  isFinalStatus,
  sinceLatestCreated,
  STATUS_META,
} from './parcelStatus';

function result(overrides: Partial<TrackingResult>): TrackingResult {
  return {
    trackingNumber: 'SD1',
    currentStatus: 'CREATED',
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
    const steps = buildTimeline(result({ currentStatus: 'CREATED' }));
    expect(steps.every((s) => s.state === 'upcoming')).toBe(true);
  });

  it('starts over for a retried parcel', () => {
    const steps = buildTimeline(
      result({
        currentStatus: 'CREATED',
        history: (['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'FAILED_ATTEMPT', 'CREATED'] as const).map(
          (statusCode) => ({ statusCode, changedAt: '2026-01-02T10:00:00Z' }),
        ),
      }),
    );
    expect(steps.every((s) => s.state === 'upcoming')).toBe(true);
  });
});

describe('status kinds', () => {
  it('treats a failed attempt as a failure that is not final', () => {
    expect(isFailureStatus('FAILED_ATTEMPT')).toBe(true);
    expect(isFinalStatus('FAILED_ATTEMPT')).toBe(false);
    expect(isFailureStatus('FAILED')).toBe(true);
    expect(isFinalStatus('FAILED')).toBe(true);
    expect(isFinalStatus('DELIVERED')).toBe(true);
    expect(isFailureStatus('OUT_FOR_DELIVERY')).toBe(false);
    expect(STATUS_META.FAILED_ATTEMPT).toEqual(
      expect.objectContaining({ label: 'Delivery attempt failed', tone: STATUS_META.FAILED.tone }),
    );
  });
});

describe('sinceLatestCreated', () => {
  it('keeps the history from the latest CREATED on', () => {
    const codes = ['CREATED', 'PICKED_UP', 'FAILED_ATTEMPT', 'CREATED', 'PICKED_UP'] as const;
    expect(sinceLatestCreated([...codes], (code) => code)).toEqual(['CREATED', 'PICKED_UP']);
    expect(sinceLatestCreated(['PICKED_UP'] as const, (code) => code)).toEqual(['PICKED_UP']);
  });
});
