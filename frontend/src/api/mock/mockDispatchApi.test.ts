import { ApiRequestError } from '../errors';
import { mockCouriersApi, resetMockCouriers } from './mockCouriersApi';
import { mockDispatchApi as api } from './mockDispatchApi';
import { mockParcelsApi, resetMockDispatchStates } from './mockParcelsApi';
import { resetMockServiceAreas } from './mockServiceAreasApi';

jest.setTimeout(15_000);

/** Admin sample parcels (mockParcelsApi): ids follow their order. */
const WAITING = 'a0000000-0000-4000-8000-000000000001'; // SD123456789, CREATED, unassigned
const ALREADY_ASSIGNED = 'a0000000-0000-4000-8000-000000000002'; // SD987654321, CREATED, assigned
const IN_TRANSIT = 'a0000000-0000-4000-8000-000000000003'; // SD456789123
const WAITING_2 = 'a0000000-0000-4000-8000-000000000006'; // SD112233445

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
