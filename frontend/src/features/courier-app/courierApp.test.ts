import { courierActions } from './actions';
import { compactTrackingNumber } from './api';
import { mockCourierApi, resetMockCourierData } from './mock/mockCourierApi';

describe('courierActions', () => {
  const id = 'p1';

  it('sends each next status to the screen that records it', () => {
    expect(courierActions(id, ['OUT_FOR_DELIVERY', 'FAILED'])).toEqual([
      expect.objectContaining({
        label: 'Mark Out for Delivery',
        to: '/courier/deliveries/p1/status?status=OUT_FOR_DELIVERY',
      }),
      expect.objectContaining({
        label: 'Report Failed Delivery',
        to: '/courier/deliveries/p1/failed',
      }),
    ]);
    expect(courierActions(id, ['DELIVERED'])[0].to).toBe('/courier/deliveries/p1/proof');
  });

  it('has no buttons for a finished delivery', () => {
    expect(courierActions(id, [])).toEqual([]);
  });
});

describe('compactTrackingNumber', () => {
  it('removes every space', () => {
    expect(compactTrackingNumber(' sd 2611 1200 01\n')).toBe('sd2611120001');
  });
});

describe('mockCourierApi', () => {
  beforeEach(() => resetMockCourierData());

  it('finds a delivery by tracking number ignoring case and spaces', async () => {
    const found = await mockCourierApi.findByTrackingNumber('sd 2611 1200 03');
    expect(found?.parcelId).toBe('7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e03');
    expect(found?.allowedActions).toEqual(['IN_TRANSIT', 'FAILED']);
  });

  it('returns null for an unknown delivery', async () => {
    expect(await mockCourierApi.findByTrackingNumber('SD0000000000')).toBeNull();
    expect(await mockCourierApi.getDelivery('nope')).toBeNull();
  });

  it('only includes the pickup stop in the list while the parcel waits for pickup', async () => {
    const { deliveries } = await mockCourierApi.getToday();
    expect(deliveries.filter((d) => d.pickup !== null).map((d) => d.currentStatus)).toEqual([
      'CREATED',
      'CREATED',
    ]);
  });
});
