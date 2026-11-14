import type { Paged } from '../../types/parcel';
import type {
  CoverageArea,
  CoverageAreaInput,
  ServiceAreaDetail,
  ServiceAreaRow,
  ServiceAreasApi,
} from '../../types/serviceArea';
import { ApiRequestError } from '../errors';
import { delay } from './delay';

/**
 * Sample delivery zones (VITE_USE_MOCK_API, and every Jest test). Follows the backend's rules
 * (docs/config.md) closely enough to exercise the screens: names unique ignoring case, a place listed
 * once per zone, no place in two active zones (checked on add and on activate), deactivate only.
 */

interface MockZone {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  coverage: CoverageArea[];
}

let nextId = 1;

function id(prefix: string): string {
  const n = String(nextId++).padStart(12, '0');
  return `${prefix}-0000-4000-8000-${n}`;
}

function place(district: string, city: string, postalCode: string | null = null): CoverageArea {
  return { id: id('c0ffee00'), district, city, postalCode, active: true };
}

function zone(
  name: string,
  description: string,
  active: boolean,
  daysAgo: number,
  coverage: CoverageArea[],
): MockZone {
  return {
    id: id('2a0e0000'),
    name,
    description,
    active,
    createdAt: new Date(Date.now() - daysAgo * 86_400_000).toISOString(),
    coverage,
  };
}

function seed(): MockZone[] {
  nextId = 1;
  return [
    zone('Colombo 01', 'Fort and the business district', true, 200, [
      place('Colombo', 'Colombo', '00100'),
    ]),
    zone('Colombo 02-07', 'Central Colombo', true, 199, [
      place('Colombo', 'Colombo', '00200'),
      place('Colombo', 'Colombo', '00300'),
      place('Colombo', 'Colombo', '00400'),
      place('Colombo', 'Colombo', '00500'),
      place('Colombo', 'Colombo', '00600'),
      place('Colombo', 'Colombo', '00700'),
    ]),
    zone('Colombo Suburbs', 'Dehiwala, Mount Lavinia, Kotte and Nugegoda', true, 180, [
      place('Colombo', 'Dehiwala', '10350'),
      place('Colombo', 'Mount Lavinia', '10370'),
      place('Colombo', 'Kotte'),
      place('Colombo', 'Nugegoda', '10250'),
    ]),
    zone('Gampaha', 'Gampaha district including Negombo', true, 170, [
      place('Gampaha', 'Gampaha', '11000'),
      place('Gampaha', 'Ja-Ela', '11350'),
      place('Gampaha', 'Negombo', '11500'),
    ]),
    zone('Kalutara', 'Western coast south of Colombo', true, 160, [
      place('Kalutara', 'Kalutara', '12000'),
      place('Kalutara', 'Panadura', '12500'),
      place('Kalutara', 'Beruwala', '12070'),
    ]),
    zone('Kandy', 'Kandy and nearby towns', true, 150, [
      place('Kandy', 'Kandy', '20000'),
      place('Kandy', 'Peradeniya', '20400'),
      place('Kandy', 'Katugastota', '20800'),
    ]),
    zone('Galle', 'Galle and the southern coast', true, 140, [
      place('Galle', 'Galle', '80000'),
      place('Galle', 'Unawatuna', '80600'),
      place('Galle', 'Hikkaduwa', '80240'),
    ]),
    zone('Matara', 'Matara district', true, 130, [
      place('Matara', 'Matara', '81000'),
      place('Matara', 'Weligama', '81700'),
    ]),
    zone('Kurunegala', 'North Western Province hub', true, 120, [
      place('Kurunegala', 'Kurunegala', '60000'),
    ]),
    zone('Jaffna', 'Northern Province', true, 110, [
      place('Jaffna', 'Jaffna', '40000'),
      place('Jaffna', 'Chavakachcheri', '40400'),
    ]),
    zone('Trincomalee', 'Eastern coast', true, 100, [place('Trincomalee', 'Trincomalee', '31000')]),
    zone('Anuradhapura', 'Not served at the moment', false, 90, [
      place('Anuradhapura', 'Anuradhapura', '50000'),
      place('Anuradhapura', 'Mihintale', '50300'),
    ]),
  ];
}

let zones: MockZone[] = seed();

/** Back to the sample data (tests). */
export function resetMockServiceAreas(): void {
  zones = seed();
}

// ---- Rules (as in the backend's ServiceAreaService) --------------------------------------------

function text(value: string | null | undefined): string | null {
  const cleaned = (value ?? '').trim().replace(/\s+/g, ' ');
  return cleaned === '' ? null : cleaned;
}

function postal(value: string | null | undefined): string | null {
  return text(value)?.toUpperCase() ?? null;
}

function sameText(a: string | null, b: string | null): boolean {
  return (a ?? '').toLowerCase() === (b ?? '').toLowerCase();
}

function samePlace(
  a: CoverageArea,
  b: { district: string; city: string; postalCode: string | null },
) {
  return (
    sameText(a.district, b.district) &&
    sameText(a.city, b.city) &&
    sameText(a.postalCode, b.postalCode)
  );
}

function describe(area: { district: string; city: string; postalCode: string | null }): string {
  const where = `${area.city}, ${area.district}`;
  return area.postalCode ? `${where} (${area.postalCode})` : where;
}

const NAME_TAKEN = 'A service area with this name already exists';

function notFound(): ApiRequestError {
  return new ApiRequestError('Service area not found', { status: 404, code: 'NOT_FOUND' });
}

function conflict(message: string, field?: string): ApiRequestError {
  return new ApiRequestError(message, {
    status: 409,
    code: 'CONFLICT',
    fieldErrors: field ? [{ field, message }] : [],
  });
}

function badRequest(message: string): ApiRequestError {
  return new ApiRequestError(message, { status: 400, code: 'BAD_REQUEST' });
}

function validName(name: string | undefined): string {
  const cleaned = text(name);
  if (!cleaned || cleaned.length < 2 || cleaned.length > 100) {
    throw badRequest('Name must be 2-100 characters');
  }
  return cleaned;
}

function newArea(input: CoverageAreaInput): CoverageArea {
  const district = text(input.district);
  const city = text(input.city);
  if (!district || !city) throw badRequest('District and city are required');
  const postalCode = postal(input.postalCode);
  if (postalCode && !/^[A-Za-z0-9 -]{3,15}$/.test(postalCode)) {
    throw badRequest('Postal code must be 3-15 letters, digits, spaces or hyphens');
  }
  return { id: id('c0ffee00'), district, city, postalCode, active: true };
}

/** 409 naming the active zone that already covers one of this zone's active places. */
function refuseOverlaps(target: MockZone, places: CoverageArea[]): void {
  for (const other of zones) {
    if (other.id === target.id || !other.active) continue;
    for (const mine of places.filter((p) => p.active)) {
      const taken = other.coverage.find((theirs) => theirs.active && samePlace(theirs, mine));
      if (taken) {
        throw conflict(
          `${describe(taken)} is already covered by the active service area "${other.name}"`,
        );
      }
    }
  }
}

function find(zoneId: string): MockZone {
  const found = zones.find((z) => z.id === zoneId);
  if (!found) throw notFound();
  return found;
}

// ---- Views -------------------------------------------------------------------------------------

function distinctSorted(values: string[]): string[] {
  const byKey = new Map<string, string>();
  for (const value of values) {
    if (!byKey.has(value.toLowerCase())) byKey.set(value.toLowerCase(), value);
  }
  return [...byKey.values()].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
}

function toRow(z: MockZone): ServiceAreaRow {
  const active = z.coverage.filter((c) => c.active);
  const codes = active
    .map((c) => c.postalCode)
    .filter((code): code is string => code !== null)
    .sort();
  return {
    id: z.id,
    name: z.name,
    description: z.description,
    active: z.active,
    cities: distinctSorted(active.map((c) => c.city)),
    districts: distinctSorted(active.map((c) => c.district)),
    postalCodeRange:
      codes.length === 0
        ? null
        : codes.length === 1
          ? codes[0]
          : `${codes[0]}-${codes[codes.length - 1]}`,
    coverageCount: active.length,
    createdAt: z.createdAt,
  };
}

function toDetail(z: MockZone): ServiceAreaDetail {
  const coverageAreas = [...z.coverage].sort(
    (a, b) =>
      a.district.localeCompare(b.district) ||
      a.city.localeCompare(b.city) ||
      (a.postalCode ?? '').localeCompare(b.postalCode ?? ''),
  );
  return { ...toRow(z), coverageAreas: coverageAreas.map((c) => ({ ...c })) };
}

function page<T>(items: T[], pageIndex = 0, size = 20): Paged<T> {
  const start = pageIndex * size;
  return {
    content: items.slice(start, start + size),
    page: {
      size,
      number: pageIndex,
      totalElements: items.length,
      totalPages: Math.ceil(items.length / size),
    },
  };
}

// ---- For the couriers mock (what the backend's ZoneApi gives dispatch) ---------------------------

/** Ids that are not an active zone. */
export function mockMissingOrInactiveZones(zoneIds: string[]): string[] {
  return zoneIds.filter((zoneId) => !zones.some((z) => z.id === zoneId && z.active));
}

/** Zone names by id, active or not. */
export function mockZoneName(zoneId: string): string | null {
  return zones.find((z) => z.id === zoneId)?.name ?? null;
}

/** Ids of the sample zones by name, so the couriers mock can refer to them. */
export function mockZoneIdByName(name: string): string {
  const found = zones.find((z) => z.name === name);
  if (!found) throw new Error(`No sample zone ${name}`);
  return found.id;
}

// ---- API -----------------------------------------------------------------------------------------

export const mockServiceAreasApi: ServiceAreasApi = {
  async list({ search, active, page: pageIndex, size }) {
    await delay(250);
    const text = search?.trim().toLowerCase();
    const found = zones
      .filter((z) => active === undefined || z.active === active)
      .filter(
        (z) =>
          !text ||
          z.name.toLowerCase().includes(text) ||
          z.coverage.some(
            (c) =>
              c.city.toLowerCase().includes(text) ||
              c.district.toLowerCase().includes(text) ||
              (c.postalCode ?? '').toLowerCase().includes(text),
          ),
      )
      .sort((a, b) => a.name.localeCompare(b.name));
    return page(found.map(toRow), pageIndex ?? 0, size ?? 20);
  },

  async get(zoneId) {
    await delay(200);
    return toDetail(find(zoneId));
  },

  async create(input) {
    await delay(300);
    const name = validName(input.name);
    if (zones.some((z) => sameText(z.name, name))) throw conflict(NAME_TAKEN, 'name');
    const coverage: CoverageArea[] = [];
    for (const area of input.coverageAreas ?? []) {
      const created = newArea(area);
      if (coverage.some((c) => samePlace(c, created))) {
        throw conflict(`${describe(created)} is listed more than once`);
      }
      coverage.push(created);
    }
    const created: MockZone = {
      id: id('2a0e0000'),
      name,
      description: text(input.description),
      active: input.active ?? true,
      createdAt: new Date().toISOString(),
      coverage,
    };
    if (created.active) refuseOverlaps(created, coverage);
    zones.push(created);
    return toDetail(created);
  },

  async update(zoneId, input) {
    await delay(250);
    if (input.name === undefined && input.description === undefined) {
      throw badRequest('Nothing to update');
    }
    const target = find(zoneId);
    if (input.name !== undefined) {
      const name = validName(input.name);
      if (zones.some((z) => z.id !== zoneId && sameText(z.name, name))) {
        throw conflict(NAME_TAKEN, 'name');
      }
      target.name = name;
    }
    if (input.description !== undefined) target.description = text(input.description);
    return toDetail(target);
  },

  async activate(zoneId) {
    await delay(250);
    const target = find(zoneId);
    if (!target.active) {
      refuseOverlaps(target, target.coverage);
      target.active = true;
    }
    return toDetail(target);
  },

  async deactivate(zoneId) {
    await delay(250);
    const target = find(zoneId);
    target.active = false;
    return toDetail(target);
  },

  async addCoverage(zoneId, input) {
    await delay(200);
    const target = find(zoneId);
    const area = newArea(input);
    if (target.coverage.some((c) => c.active && samePlace(c, area))) {
      throw conflict(`This service area already covers ${describe(area)}`);
    }
    if (target.active) refuseOverlaps(target, [area]);
    target.coverage.push(area);
    return { ...area };
  },

  async removeCoverage(zoneId, coverageId) {
    await delay(200);
    const target = find(zoneId);
    if (!target.coverage.some((c) => c.id === coverageId)) {
      throw new ApiRequestError('Coverage area not found', { status: 404, code: 'NOT_FOUND' });
    }
    target.coverage = target.coverage.filter((c) => c.id !== coverageId);
  },
};
