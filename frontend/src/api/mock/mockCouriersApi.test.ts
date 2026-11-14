import type { NewCourier } from '../../types/courier';
import { mockCouriersApi as api, resetMockCouriers } from './mockCouriersApi';
import { mockZoneIdByName, resetMockServiceAreas } from './mockServiceAreasApi';

const input: NewCourier = {
  fullName: 'Amaya Gunawardena',
  email: 'amaya@example.com',
  phone: '+94771239876',
  password: 'courier2026',
  vehicleType: 'Motorbike',
  vehicleRegistration: 'wp  cab 1234',
};

describe('mockCouriersApi', () => {
  beforeEach(() => {
    resetMockServiceAreas();
    resetMockCouriers();
  });

  it('counts active couriers by availability', async () => {
    const summary = await api.summary();

    expect(summary.online + summary.onBreak + summary.offline).toBe(summary.total);
    expect(summary.total).toBe(11); // the deactivated sample is left out
  });

  it('works the workload out from the assignments', async () => {
    const ramesh = (await api.list({ search: 'ramesh' })).content[0];

    expect(ramesh.workload).toEqual({
      assigned: 1,
      inProgress: 2,
      completedToday: 12,
      open: 3,
      todaysTotal: 15,
    });
    const current = await api.listAssignments(ramesh.id, { open: true });
    expect(current.content).toHaveLength(3);
    expect(current.content.every((a) => a.endedAt === null)).toBe(true);
  });

  it('filters by availability, service area and vehicle type', async () => {
    const kandy = mockZoneIdByName('Kandy');

    const found = await api.list({
      serviceAreaId: kandy,
      availability: 'ONLINE',
      vehicleType: 'motorbike',
    });

    expect(found.content.map((c) => c.fullName)).toEqual(['Indika Weerasinghe']);
  });

  it('adds a courier offline with a normalised registration', async () => {
    const created = await api.create({ ...input, serviceAreaIds: [mockZoneIdByName('Galle')] });

    expect(created.availability).toBe('OFFLINE');
    expect(created.vehicleRegistration).toBe('WP CAB 1234');
    expect(created.serviceAreas).toEqual([{ id: mockZoneIdByName('Galle'), name: 'Galle' }]);
  });

  it('refuses a taken email or vehicle registration with a 409 on the field', async () => {
    await expect(
      api.create({ ...input, email: 'RAMESH.perera@swiftdrop.lk' }),
    ).rejects.toMatchObject({
      status: 409,
      fieldErrors: [{ field: 'email' }],
    });
    await expect(api.create({ ...input, vehicleRegistration: 'abc-1234' })).rejects.toMatchObject({
      status: 409,
      fieldErrors: [{ field: 'vehicleRegistration' }],
    });
  });

  it('refuses an inactive service area with a 400', async () => {
    await expect(
      api.create({ ...input, serviceAreaIds: [mockZoneIdByName('Anuradhapura')] }),
    ).rejects.toMatchObject({ status: 400, message: 'Unknown or inactive service area' });
  });

  it('checks the same field rules as the backend', async () => {
    await expect(
      api.create({ ...input, fullName: 'R2D2', phone: '0771234567', password: 'password' }),
    ).rejects.toMatchObject({
      status: 400,
      fieldErrors: expect.arrayContaining([
        expect.objectContaining({ field: 'fullName' }),
        expect.objectContaining({ field: 'phone' }),
        expect.objectContaining({ field: 'password' }),
      ]),
    });
  });

  it('keeps a deactivated courier offline', async () => {
    const chamari = (await api.list({ search: 'chamari' })).content[0];

    await expect(api.changeAvailability(chamari.id, 'ONLINE')).rejects.toMatchObject({
      status: 409,
    });
    await expect(api.changeAvailability(chamari.id, 'ON_BREAK')).resolves.toMatchObject({
      availability: 'ON_BREAK',
    });
  });

  it('replaces the service areas on update', async () => {
    const praveen = (await api.list({ search: 'praveen' })).content[0];

    const updated = await api.update(praveen.id, {
      serviceAreaIds: [mockZoneIdByName('Kurunegala')],
    });

    expect(updated.serviceAreas.map((a) => a.name)).toEqual(['Kurunegala']);
  });
});
