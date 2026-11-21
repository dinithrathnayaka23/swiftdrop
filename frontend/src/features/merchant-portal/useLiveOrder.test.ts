import type { LiveStatusUpdate } from '../shared-live/types';
import type { MerchantOrderDetail } from './types';
import { withUpdate } from './useLiveOrder';

const ORDER = {
  trackingNumber: 'SD1',
  status: 'PICKED_UP',
  updatedAt: '2026-11-10T09:00:00Z',
  timeline: [
    {
      status: 'CREATED',
      changedAt: '2026-11-10T08:00:00Z',
      note: null,
      failureReason: null,
      recipientName: null,
    },
    {
      status: 'PICKED_UP',
      changedAt: '2026-11-10T09:00:00Z',
      note: 'At the depot',
      failureReason: null,
      recipientName: null,
    },
  ],
} as MerchantOrderDetail;

function update(overrides: Partial<LiveStatusUpdate>): LiveStatusUpdate {
  return {
    trackingNumber: 'SD1',
    previousStatus: 'PICKED_UP',
    status: 'IN_TRANSIT',
    statusVersion: 3,
    changedAt: '2026-11-10T10:00:00Z',
    ...overrides,
  };
}

test('a newer update moves the order on and adds it to the timeline', () => {
  const next = withUpdate(ORDER, update({}));

  expect(next.status).toBe('IN_TRANSIT');
  expect(next.updatedAt).toBe('2026-11-10T10:00:00Z');
  expect(next.timeline).toHaveLength(3);
  expect(next.timeline[2]).toEqual({
    status: 'IN_TRANSIT',
    changedAt: '2026-11-10T10:00:00Z',
    note: null,
    failureReason: null,
    recipientName: null,
  });
  expect(next.timeline[1].note).toBe('At the depot');
});

test.each([1, 2])('an update with version %i (already shown) changes nothing', (statusVersion) => {
  expect(withUpdate(ORDER, update({ statusVersion }))).toBe(ORDER);
});

test("another parcel's update changes nothing", () => {
  expect(withUpdate(ORDER, update({ trackingNumber: 'SD2' }))).toBe(ORDER);
});
