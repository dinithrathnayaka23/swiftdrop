import type { ParcelStatusCode, TrackingResult } from '../../types/tracking';
import { buildTrackingTimeline } from './trackingSteps';

function result(currentStatus: ParcelStatusCode, steps: ParcelStatusCode[]): TrackingResult {
  return {
    trackingNumber: 'SD1',
    currentStatus,
    bookedAt: '2026-01-01T10:00:00Z',
    lastUpdatedAt: '2026-01-03T10:00:00Z',
    fromCity: 'Colombo',
    toCity: 'Kandy',
    weightKg: 1,
    history: steps.map((statusCode, i) => ({
      statusCode,
      changedAt: `2026-01-0${i + 1}T10:00:00Z`,
    })),
  };
}

const states = (r: TrackingResult) => buildTrackingTimeline(r).map((step) => step.state);

describe('buildTrackingTimeline', () => {
  it('shows no progress for a booked parcel', () => {
    const steps = buildTrackingTimeline(result('CREATED', ['CREATED']));

    expect(steps.map((s) => s.state)).toEqual(['upcoming', 'upcoming', 'upcoming', 'upcoming']);
    expect(steps.every((s) => s.reachedAt === undefined)).toBe(true);
  });

  it('marks reached steps complete and the latest one current', () => {
    expect(states(result('IN_TRANSIT', ['CREATED', 'PICKED_UP', 'IN_TRANSIT']))).toEqual([
      'complete',
      'current',
      'upcoming',
      'upcoming',
    ]);
  });

  it('marks every step complete once delivered', () => {
    const delivered = result('DELIVERED', [
      'CREATED',
      'PICKED_UP',
      'IN_TRANSIT',
      'OUT_FOR_DELIVERY',
      'DELIVERED',
    ]);

    expect(states(delivered)).toEqual(['complete', 'complete', 'complete', 'complete']);
  });

  it('stops a failed delivery at the last step reached, with the failure time', () => {
    const steps = buildTrackingTimeline(
      result('FAILED', ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'FAILED']),
    );

    expect(steps.map((s) => s.state)).toEqual(['complete', 'failed', 'upcoming', 'upcoming']);
    expect(steps[1].reachedAt).toBe('2026-01-04T10:00:00Z');
    expect(steps[2].reachedAt).toBeUndefined();
  });

  it('still shows the failure when the history has no pickup', () => {
    const steps = buildTrackingTimeline(result('FAILED', ['CREATED']));

    expect(steps.map((s) => s.state)).toEqual(['failed', 'upcoming', 'upcoming', 'upcoming']);
    expect(steps[0].reachedAt).toBe('2026-01-03T10:00:00Z');
  });
});
