import {
  mockMissingOrInactiveZones,
  mockServiceAreasApi as api,
  mockZoneIdByName,
  resetMockServiceAreas,
} from './mockServiceAreasApi';

describe('mockServiceAreasApi', () => {
  beforeEach(() => resetMockServiceAreas());

  it('lists zones by name with a summary of their active coverage', async () => {
    const all = await api.list({ size: 100 });
    const names = all.content.map((z) => z.name);

    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b)));
    const kandy = all.content.find((z) => z.name === 'Kandy')!;
    expect(kandy.cities).toEqual(['Katugastota', 'Kandy', 'Peradeniya'].sort());
    expect(kandy.postalCodeRange).toBe('20000-20800');
    expect(kandy.coverageCount).toBe(3);
  });

  it('searches names and coverage, and filters by status', async () => {
    expect((await api.list({ search: 'unawat' })).content.map((z) => z.name)).toEqual(['Galle']);
    const inactive = await api.list({ active: false });
    expect(inactive.content.map((z) => z.name)).toEqual(['Anuradhapura']);
  });

  it('refuses a name that is taken in another case (409 on the name field)', async () => {
    await expect(api.create({ name: '  kandy ' })).rejects.toMatchObject({
      status: 409,
      fieldErrors: [{ field: 'name' }],
    });
  });

  it('refuses a place another active zone covers, naming that zone', async () => {
    await expect(
      api.create({
        name: 'Hill Country',
        coverageAreas: [{ district: 'KANDY', city: ' peradeniya ', postalCode: '20400' }],
      }),
    ).rejects.toMatchObject({
      status: 409,
      message: 'Peradeniya, Kandy (20400) is already covered by the active service area "Kandy"',
    });
  });

  it('lets an inactive zone list a taken place but not be activated', async () => {
    const parked = await api.create({
      name: 'Hill Country',
      active: false,
      coverageAreas: [{ district: 'Kandy', city: 'Peradeniya', postalCode: '20400' }],
    });

    await expect(api.activate(parked.id)).rejects.toMatchObject({ status: 409 });
    await api.deactivate(mockZoneIdByName('Kandy'));
    await expect(api.activate(parked.id)).resolves.toMatchObject({ active: true });
  });

  it('adds and removes coverage rows', async () => {
    const zoneId = mockZoneIdByName('Matara');
    const added = await api.addCoverage(zoneId, {
      district: 'Matara',
      city: 'Mirissa',
      postalCode: '81740',
    });
    await expect(
      api.addCoverage(zoneId, { district: 'matara', city: 'mirissa', postalCode: '81740' }),
    ).rejects.toMatchObject({ status: 409 });

    await api.removeCoverage(zoneId, added.id);
    expect((await api.get(zoneId)).coverageAreas.map((c) => c.city)).not.toContain('Mirissa');
    await expect(api.removeCoverage(zoneId, added.id)).rejects.toMatchObject({ status: 404 });
  });

  it('tells the couriers mock which zones are unknown or inactive', () => {
    const active = mockZoneIdByName('Kandy');
    const inactive = mockZoneIdByName('Anuradhapura');

    expect(mockMissingOrInactiveZones([active, inactive, 'nope'])).toEqual([inactive, 'nope']);
  });
});
