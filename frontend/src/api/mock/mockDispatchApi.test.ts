import { ApiRequestError } from '../errors';
import { mockCouriersApi, resetMockCouriers } from './mockCouriersApi';
import {
  mockDispatchApi as api,
  resetMockFailedDeliveries,
  setMockDefaultFailedAction,
} from './mockDispatchApi';
import { mockParcelsApi, resetMockDispatchStates } from './mockParcelsApi';
import { resetMockServiceAreas } from './mockServiceAreasApi';

jest.setTimeout(15_000);

/** Admin sample parcels (mockParcelsApi): ids follow their order. */
const WAITING = 'a0000000-0000-4000-8000-000000000001'; // SD123456789, CREATED, unassigned
const ALREADY_ASSIGNED = 'a0000000-0000-4000-8000-000000000002'; // SD987654321, CREATED, assigned
const IN_TRANSIT = 'a0000000-0000-4000-8000-000000000003'; // SD456789123
const WAITING_2 = 'a0000000-0000-4000-8000-000000000006'; // SD112233445
const FAILED_ATTEMPT = 'a0000000-0000-4000-8000-000000000007'; // SD998877665

async function courierId(name: string): Promise<string> {
  return (await mockCouriersApi.list({ search: name })).content[0].id;
}

async function failure(call: Promise<unknown>): Promise<ApiRequestError> {
  const error = await call.catch((e: unknown) => e);
  expect(error).toBeInstanceOf(ApiRequestError);
  return error as ApiRequestError;
}

async function unassignedIds(): Promise<string[]> {
  return (await mockParcelsApi.listAdminParcels({ tab: 'UNASSIGNED', size: 100 })).content.map(
    (p) => p.id,
  );
}

describe('mockDispatchApi', () => {
  beforeEach(() => {
    resetMockServiceAreas();
    resetMockCouriers();
    resetMockDispatchStates();
    resetMockFailedDeliveries();
  });

  it('assigns a waiting parcel to an online courier', async () => {
    const ramesh = await courierId('Ramesh');
    const before = (await mockCouriersApi.get(ramesh)).workload.assigned;

    const assignment = await api.assign(WAITING, ramesh);

    expect(assignment).toMatchObject({ parcelId: WAITING, courierId: ramesh, status: 'ASSIGNED' });
    expect(await unassignedIds()).not.toContain(WAITING);
    expect((await mockCouriersApi.get(ramesh)).workload.assigned).toBe(before + 1);
    await expect(api.getAssignment(assignment.id)).resolves.toMatchObject({ id: assignment.id });
  });

  it('refuses parcels that are picked up, already assigned or unknown', async () => {
    const ramesh = await courierId('Ramesh');
    expect((await failure(api.assign(IN_TRANSIT, ramesh))).message).toBe(
      'Only a parcel that has not been picked up yet can be assigned',
    );
    expect((await failure(api.assign(ALREADY_ASSIGNED, ramesh))).status).toBe(409);
    await api.assign(WAITING, ramesh);
    expect((await failure(api.assign(WAITING, ramesh))).message).toBe(
      'This parcel is already assigned to a courier',
    );
    expect((await failure(api.assign('nope', ramesh))).status).toBe(404);
  });

  it('refuses couriers on a break, offline or deactivated', async () => {
    expect((await failure(api.assign(WAITING, await courierId('Kasun')))).message).toBe(
      'This courier is on a break and cannot be given parcels',
    );
    expect((await failure(api.assign(WAITING, await courierId('Sameera')))).message).toBe(
      'This courier is offline and cannot be given parcels',
    );
    expect((await failure(api.assign(WAITING, await courierId('Chamari')))).message).toBe(
      'This courier is deactivated and cannot be given parcels',
    );
  });

  it('bulk assigns each parcel on its own and reports the failures', async () => {
    const ramesh = await courierId('Ramesh');

    const result = await api.assignAll(ramesh, [WAITING, IN_TRANSIT, WAITING_2, WAITING]);

    expect(result.assigned.map((a) => a.parcelId)).toEqual([WAITING, WAITING_2]);
    expect(result.failed).toEqual([
      {
        parcelId: IN_TRANSIT,
        reason: 'Only a parcel that has not been picked up yet can be assigned',
      },
    ]);
  });

  it('checks the bulk size like BulkAssignRequest', async () => {
    const ramesh = await courierId('Ramesh');
    expect((await failure(api.assignAll(ramesh, []))).fieldErrors[0].field).toBe('parcelIds');
    const tooMany = Array.from({ length: 51 }, (_, i) => `p${i}`);
    expect((await failure(api.assignAll(ramesh, tooMany))).message).toBe(
      'Request validation failed',
    );
  });

  it('reassigns with a reason to another online courier only', async () => {
    const ramesh = await courierId('Ramesh');
    const dilshan = await courierId('Dilshan');
    await api.assign(WAITING, ramesh);

    expect((await failure(api.reassign(WAITING, dilshan, '  '))).fieldErrors).toEqual([
      { field: 'reason', message: 'Reason is required' },
    ]);
    expect((await failure(api.reassign(WAITING, ramesh, 'Again'))).message).toBe(
      'This parcel is already assigned to this courier',
    );
    expect((await failure(api.reassign(WAITING_2, dilshan, 'No courier'))).message).toBe(
      'This parcel has no courier yet: assign it instead',
    );

    const moved = await api.reassign(WAITING, dilshan, ' Vehicle broke down ');
    expect(moved).toMatchObject({ courierId: dilshan, status: 'ASSIGNED' });
    const history = await mockCouriersApi.listAssignments(ramesh, { open: false });
    expect(history.content.find((a) => a.parcelId === WAITING)).toMatchObject({
      status: 'REASSIGNED',
      reason: 'Vehicle broke down',
    });
  });

  it('lists failed attempts and closed parcels, newest booking first', async () => {
    const attention = await api.listFailed({ scope: 'ATTENTION' });
    expect(attention.content.map((r) => r.trackingNumber)).toEqual(['SD998877665']);
    expect(attention.content[0]).toMatchObject({
      parcelStatus: 'FAILED_ATTEMPT',
      failureReason: 'Recipient not available',
      destinationCity: 'Kottawa',
    });
    const closed = await api.listFailed({ scope: 'CLOSED' });
    expect(closed.content.map((r) => r.parcelStatus)).toEqual(['FAILED', 'FAILED']);
    expect((await failure(api.listFailed({ scope: 'CLOSED', size: 51 }))).status).toBe(400);
  });

  it('retries a failed attempt: back to Unassigned, once only', async () => {
    expect((await failure(api.retry(FAILED_ATTEMPT, 'x'.repeat(501)))).fieldErrors[0].field).toBe(
      'note',
    );
    await expect(api.retry(FAILED_ATTEMPT, 'Home after 5 PM')).resolves.toEqual({
      parcelId: FAILED_ATTEMPT,
      status: 'CREATED',
    });
    expect(await unassignedIds()).toContain(FAILED_ATTEMPT);
    expect((await api.listFailed({ scope: 'ATTENTION' })).content).toHaveLength(0);
    expect((await failure(api.retry(FAILED_ATTEMPT))).message).toBe(
      'Only a parcel whose delivery attempt failed can be retried or closed',
    );
    // Assigned again with the normal flow.
    await expect(api.assign(FAILED_ATTEMPT, await courierId('Ramesh'))).resolves.toBeDefined();
  });

  it('closes a failed attempt with a reason; it moves to the closed list', async () => {
    expect((await failure(api.close(FAILED_ATTEMPT, ' '))).fieldErrors[0]).toEqual({
      field: 'reason',
      message: 'Reason is required',
    });
    expect((await failure(api.close(WAITING, 'No'))).status).toBe(409);
    expect((await failure(api.close('nope', 'No'))).status).toBe(404);

    await expect(api.close(FAILED_ATTEMPT, ' Returned to sender ')).resolves.toEqual({
      parcelId: FAILED_ATTEMPT,
      status: 'FAILED',
    });
    const closed = (await api.listFailed({ scope: 'CLOSED' })).content;
    expect(closed.find((r) => r.parcelId === FAILED_ATTEMPT)).toMatchObject({
      failureReason: 'Returned to sender',
    });
  });

  it('reports the default failed-delivery action', async () => {
    await expect(api.defaultFailedAction()).resolves.toBe('RETRY_DELIVERY');
    setMockDefaultFailedAction('RETURN_TO_SENDER');
    await expect(api.defaultFailedAction()).resolves.toBe('RETURN_TO_SENDER');
    expect((await api.overview()).defaultAction).toBe('RETURN_TO_SENDER');
  });

  it('builds the overview from the same data as the other mocks', async () => {
    const overview = await api.overview();
    const summary = await mockCouriersApi.summary();
    const active = await api.activeDeliveries({ size: 50 });

    expect(overview.kpis.unassignedParcels).toBe((await unassignedIds()).length);
    expect(overview.kpis).toMatchObject({
      couriersOnline: summary.online,
      couriersOnBreak: summary.onBreak,
      couriersTotal: summary.total,
      activeDeliveries: active.page.totalElements,
      needsDecision: 1,
    });
    expect(overview.todayStatus.inTransit + overview.todayStatus.outForDelivery).toBe(
      overview.kpis.activeDeliveries,
    );
    expect(overview.todayStatus.completed).toBe(overview.kpis.completedToday);
    expect(overview.courierWorkload).toHaveLength(5);
    expect(overview.courierWorkload[0]).toMatchObject({ name: 'Ramesh Perera', todaysTotal: 15 });
    const totals = overview.courierWorkload.map((c) => c.todaysTotal);
    expect([...totals].sort((a, b) => b - a)).toEqual(totals);
  });

  it('assigning a waiting parcel lowers the unassigned count', async () => {
    const before = (await api.overview()).kpis.unassignedParcels;
    await api.assign(WAITING, await courierId('Ramesh'));
    expect((await api.overview()).kpis.unassignedParcels).toBe(before - 1);
  });

  it('unassigns only before pickup, and the parcel waits for a courier again', async () => {
    const ramesh = await courierId('Ramesh');
    const open = (await mockCouriersApi.listAssignments(ramesh, { open: true })).content;
    const pickedUp = open.find((a) => a.parcelStatus !== 'CREATED')!;
    expect((await failure(api.unassign(pickedUp.parcelId))).message).toBe(
      'Only a parcel that has not been picked up yet can be unassigned',
    );

    await api.assign(WAITING, ramesh);
    const ended = await api.unassign(WAITING);

    expect(ended).toMatchObject({ status: 'CANCELLED', reason: null });
    expect(ended.endedAt).not.toBeNull();
    expect(await unassignedIds()).toContain(WAITING);
    expect((await failure(api.unassign(WAITING))).message).toBe(
      'This parcel is not assigned to a courier',
    );
  });
});
