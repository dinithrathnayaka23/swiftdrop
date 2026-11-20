import { courierActions } from './actions';
import { compactTrackingNumber } from './api';
import { mockCourierApi, resetMockCourierData } from './mock/mockCourierApi';

describe('courierActions', () => {
  const id = 'p1';

  it('sends each next status to the screen that records it', () => {
    expect(courierActions(id, ['OUT_FOR_DELIVERY', 'FAILED_ATTEMPT'])).toEqual([
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
    expect(found?.allowedActions).toEqual(['IN_TRANSIT', 'FAILED_ATTEMPT']);
  });

  it('returns null for an unknown delivery', async () => {
    expect(await mockCourierApi.findByTrackingNumber('SD0000000000')).toBeNull();
    expect(await mockCourierApi.getDelivery('nope')).toBeNull();
  });

  it('moves a delivery one step on and adds it to the history', async () => {
    const id = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e03'; // PICKED_UP
    const after = await mockCourierApi.updateStatus(id, {
      status: 'IN_TRANSIT',
      note: ' Left hub ',
    });

    expect(after.currentStatus).toBe('IN_TRANSIT');
    expect(after.allowedActions).toEqual(['OUT_FOR_DELIVERY', 'FAILED_ATTEMPT']);
    expect(after.timeline.at(-1)).toEqual(
      expect.objectContaining({ status: 'IN_TRANSIT', note: 'Left hub' }),
    );
  });

  it('refuses a step the parcel cannot take (409) and an unknown delivery (404)', async () => {
    const id = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e04'; // CREATED
    await expect(mockCourierApi.updateStatus(id, { status: 'IN_TRANSIT' })).rejects.toMatchObject({
      status: 409,
    });
    await expect(
      mockCourierApi.updateStatus('nope', { status: 'PICKED_UP' }),
    ).rejects.toMatchObject({ status: 404 });
  });

  it('lists the failure reasons with the ones that need a note', async () => {
    const reasons = await mockCourierApi.getFailureReasons();
    expect(reasons.map((r) => r.code)).toEqual([
      'RECIPIENT_UNAVAILABLE',
      'WRONG_ADDRESS',
      'REFUSED',
      'ACCESS_ISSUE',
      'DAMAGED',
      'OTHER',
    ]);
    expect(reasons.filter((r) => r.noteRequired).map((r) => r.code)).toEqual(['DAMAGED', 'OTHER']);
  });

  it('reports a failure with the reason text and drops it from today’s list', async () => {
    const id = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01'; // OUT_FOR_DELIVERY
    const after = await mockCourierApi.reportFailure(id, {
      reasonCode: 'OTHER',
      note: ' Gate locked ',
    });

    expect(after.currentStatus).toBe('FAILED_ATTEMPT');
    expect(after.allowedActions).toEqual([]);
    expect(after.timeline.at(-1)?.failureReason).toBe('Other: Gate locked');
    expect((await mockCourierApi.getToday()).deliveries.map((d) => d.parcelId)).not.toContain(id);
    expect(await mockCourierApi.getDelivery(id)).toBeNull();
  });

  it('refuses a failure without a required note, before pickup, or for an unknown reason', async () => {
    const out = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01';
    const waiting = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e04';
    await expect(
      mockCourierApi.reportFailure(out, { reasonCode: 'DAMAGED' }),
    ).rejects.toMatchObject({
      status: 400,
    });
    await expect(mockCourierApi.reportFailure(out, { reasonCode: 'NOPE' })).rejects.toMatchObject({
      status: 400,
    });
    await expect(
      mockCourierApi.reportFailure(waiting, { reasonCode: 'REFUSED' }),
    ).rejects.toMatchObject({ status: 409 });
  });

  it('delivers a parcel that is out for delivery and drops it from today’s list', async () => {
    const id = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01'; // OUT_FOR_DELIVERY
    const after = await mockCourierApi.deliver(id, {
      recipientName: 'Kasun Perera',
      note: ' At the door ',
    });

    expect(after.currentStatus).toBe('DELIVERED');
    expect(after.allowedActions).toEqual([]);
    expect(after.timeline.at(-1)).toEqual(
      expect.objectContaining({ status: 'DELIVERED', note: 'At the door' }),
    );
    expect((await mockCourierApi.getToday()).deliveries.map((d) => d.parcelId)).not.toContain(id);
  });

  it('refuses a delivery without a name (400) or before out for delivery (409)', async () => {
    const out = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01';
    const inTransit = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e02';
    await expect(mockCourierApi.deliver(out, { recipientName: ' ' })).rejects.toMatchObject({
      status: 400,
    });
    await expect(
      mockCourierApi.deliver(inTransit, { recipientName: 'Nadeesha' }),
    ).rejects.toMatchObject({ status: 409 });
  });

  it('adds deliveries and failed attempts to the completed list and counts today’s deliveries', async () => {
    const before = await mockCourierApi.getToday();
    await mockCourierApi.deliver('7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01', {
      recipientName: 'Kasun Perera',
    });
    await mockCourierApi.reportFailure('7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e02', {
      reasonCode: 'REFUSED',
    });

    const page = await mockCourierApi.getCompleted({ page: 0 });
    expect(page.content.slice(0, 2).map((c) => c.trackingNumber)).toEqual([
      'SD2611120002',
      'SD2611120001',
    ]);
    expect(page.content[0]).toEqual(
      expect.objectContaining({ outcome: 'FAILED_ATTEMPT', failureReason: 'Refused by recipient' }),
    );
    expect((await mockCourierApi.getToday()).counts.completedToday).toBe(
      before.counts.completedToday + 1,
    );
    const failedOnly = await mockCourierApi.getCompleted({ page: 0, outcome: 'FAILED_ATTEMPT' });
    expect(failedOnly.content.every((c) => c.outcome === 'FAILED_ATTEMPT')).toBe(true);
  });

  it('keeps the courier profile with today’s numbers and its status', async () => {
    const profile = await mockCourierApi.getProfile();
    expect(profile).toEqual(
      expect.objectContaining({ fullName: 'Ramesh Perera', status: 'ONLINE', assigned: 2 }),
    );
    expect((await mockCourierApi.changeStatus('OFFLINE')).status).toBe('OFFLINE');
    expect((await mockCourierApi.getProfile()).status).toBe('OFFLINE');
  });

  it('adds a sample delivery to today’s list and sets the courier online', async () => {
    await mockCourierApi.changeStatus('ON_BREAK');
    const sample = await mockCourierApi.addSampleDelivery();

    const today = await mockCourierApi.getToday();
    expect(today.deliveries.map((d) => d.trackingNumber)).toContain(sample.trackingNumber);
    expect(await mockCourierApi.getDelivery(sample.parcelId)).not.toBeNull();
    expect((await mockCourierApi.getProfile()).status).toBe('ONLINE');
  });

  it('only includes the pickup stop in the list while the parcel waits for pickup', async () => {
    const { deliveries } = await mockCourierApi.getToday();
    expect(deliveries.filter((d) => d.pickup !== null).map((d) => d.currentStatus)).toEqual([
      'CREATED',
      'CREATED',
    ]);
  });
});
