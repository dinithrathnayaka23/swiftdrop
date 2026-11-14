import type {
  AssignmentStatus,
  Courier,
  CourierAssignmentRow,
  CourierAvailability,
  CouriersApi,
  CourierWorkload,
  NewCourier,
} from '../../types/courier';
import type { Paged } from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';
import { ApiRequestError } from '../errors';
import { delay } from './delay';
import { mockMissingOrInactiveZones, mockZoneIdByName, mockZoneName } from './mockServiceAreasApi';

/**
 * Sample couriers (VITE_USE_MOCK_API, and every Jest test). Follows the backend's rules
 * (docs/dispatch.md) closely enough to exercise the screens: unique email and vehicle registration
 * (409 on the field), service areas must be active zones (400), a deactivated courier can't go online
 * (409). Workloads are worked out from the sample assignments, like the backend does.
 */

interface MockCourier {
  id: string;
  userId: string;
  fullName: string;
  email: string;
  phone: string;
  vehicleType: string;
  vehicleRegistration: string;
  availability: CourierAvailability;
  active: boolean;
  accountStatus: Courier['accountStatus'];
  serviceAreaIds: string[];
  createdAt: string;
}

interface MockAssignment extends CourierAssignmentRow {
  courierId: string;
}

const HOUR = 3_600_000;
const SRI_LANKA_OFFSET = 5.5 * HOUR;

/** Midnight today, Sri Lanka time. */
function startOfToday(now = Date.now()): number {
  const local = now + SRI_LANKA_OFFSET;
  return local - (local % (24 * HOUR)) - SRI_LANKA_OFFSET;
}

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * HOUR).toISOString();
}

let couriers: MockCourier[] = [];
let assignments: MockAssignment[] = [];
let counter = 1;

function uuid(prefix: string): string {
  return `${prefix}-0000-4000-8000-${String(counter++).padStart(12, '0')}`;
}

interface Sample {
  name: string;
  vehicle: string;
  registration: string;
  availability: CourierAvailability;
  zones: string[];
  /** open ASSIGNED, open IN_PROGRESS, completed today */
  work: [number, number, number];
  /** completed earlier (history) */
  done: number;
  joinedDaysAgo: number;
  active?: boolean;
}

const SAMPLES: Sample[] = [
  {
    name: 'Ramesh Perera',
    vehicle: 'Motorbike',
    registration: 'ABC-1234',
    availability: 'ONLINE',
    zones: ['Colombo 01', 'Colombo 02-07'],
    work: [1, 2, 12],
    done: 6,
    joinedDaysAgo: 40,
  },
  {
    name: 'Dilshan Silva',
    vehicle: 'Van',
    registration: 'WP CB 5678',
    availability: 'ONLINE',
    zones: ['Colombo Suburbs'],
    work: [1, 1, 10],
    done: 4,
    joinedDaysAgo: 60,
  },
  {
    name: 'Nuwan Fernando',
    vehicle: 'Motorbike',
    registration: 'XYZ-9876',
    availability: 'ONLINE',
    zones: ['Colombo Suburbs', 'Gampaha'],
    work: [2, 1, 11],
    done: 3,
    joinedDaysAgo: 80,
  },
  {
    name: 'Kasun Perera',
    vehicle: 'Motorbike',
    registration: 'ABF-4321',
    availability: 'ON_BREAK',
    zones: ['Colombo Suburbs'],
    work: [3, 2, 5],
    done: 2,
    joinedDaysAgo: 100,
  },
  {
    name: 'Tharindu Jayasekara',
    vehicle: 'Van',
    registration: 'WP CA 1122',
    availability: 'ONLINE',
    zones: ['Colombo 02-07'],
    work: [2, 2, 8],
    done: 5,
    joinedDaysAgo: 120,
  },
  {
    name: 'Sameera Mathews',
    vehicle: 'Motorbike',
    registration: 'XYZ-3344',
    availability: 'OFFLINE',
    zones: ['Kandy'],
    work: [0, 0, 0],
    done: 7,
    joinedDaysAgo: 140,
  },
  {
    name: 'Indika Weerasinghe',
    vehicle: 'Motorbike',
    registration: 'BCF-7788',
    availability: 'ONLINE',
    zones: ['Kandy'],
    work: [1, 1, 9],
    done: 2,
    joinedDaysAgo: 160,
  },
  {
    name: 'Dinesh Lakmal',
    vehicle: 'Van',
    registration: 'WP CB 8899',
    availability: 'ONLINE',
    zones: ['Galle', 'Matara'],
    work: [2, 1, 7],
    done: 1,
    joinedDaysAgo: 180,
  },
  {
    name: 'Praveen Silva',
    vehicle: 'Motorbike',
    registration: 'ABC-5566',
    availability: 'OFFLINE',
    zones: ['Anuradhapura', 'Kurunegala'],
    work: [0, 0, 0],
    done: 3,
    joinedDaysAgo: 200,
  },
  {
    name: 'Hasitha Kumara',
    vehicle: 'Motorbike',
    registration: 'XYZ-2288',
    availability: 'ONLINE',
    zones: ['Gampaha'],
    work: [2, 2, 6],
    done: 2,
    joinedDaysAgo: 220,
  },
  {
    name: 'Ruwan Bandara',
    vehicle: 'Lorry',
    registration: 'NC LB 4455',
    availability: 'ONLINE',
    zones: [],
    work: [1, 0, 3],
    done: 1,
    joinedDaysAgo: 240,
  },
  {
    name: 'Chamari Dias',
    vehicle: 'Motorbike',
    registration: 'WP BKM 7710',
    availability: 'OFFLINE',
    zones: ['Jaffna'],
    work: [0, 0, 0],
    done: 4,
    joinedDaysAgo: 20,
    active: false,
  },
];

const CITIES = [
  'Colombo 07',
  'Dehiwala',
  'Nugegoda',
  'Mount Lavinia',
  'Kotte',
  'Kandy',
  'Galle',
  'Negombo',
];
const ON_THE_WAY: ParcelStatusCode[] = ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'];

function addAssignment(
  courierId: string,
  status: AssignmentStatus,
  parcelStatus: ParcelStatusCode,
  assignedAt: string,
  endedAt: string | null,
  reason: string | null = null,
) {
  const n = counter;
  assignments.push({
    id: uuid('a5510000'),
    parcelId: uuid('9a5ce100'),
    courierId,
    status,
    assignedAt,
    endedAt,
    reason,
    trackingNumber: `SD26111${String(n).padStart(5, '0')}`,
    parcelStatus,
    destinationCity: CITIES[n % CITIES.length],
  });
}

function seed() {
  counter = 1;
  couriers = [];
  assignments = [];
  const midnight = startOfToday();
  const sinceMidnight = Math.max(0.5, (Date.now() - midnight) / HOUR);
  SAMPLES.forEach((sample, i) => {
    const first = sample.name.split(' ')[0].toLowerCase();
    const courier: MockCourier = {
      id: uuid('c0010000'),
      userId: uuid('05e10000'),
      fullName: sample.name,
      email: `${first}.${sample.name.split(' ')[1].toLowerCase()}@swiftdrop.lk`,
      phone: `+9477${String(1234567 + i * 1111).slice(0, 7)}`,
      vehicleType: sample.vehicle,
      vehicleRegistration: sample.registration,
      availability: sample.availability,
      active: sample.active ?? true,
      accountStatus: sample.active === false ? 'DEACTIVATED' : 'ACTIVE',
      serviceAreaIds: sample.zones.map(mockZoneIdByName),
      createdAt: hoursAgo(sample.joinedDaysAgo * 24),
    };
    couriers.push(courier);
    const [assigned, inProgress, completedToday] = sample.work;
    for (let k = 0; k < completedToday; k++) {
      const ended = midnight + ((k + 1) / (completedToday + 1)) * sinceMidnight * HOUR;
      addAssignment(
        courier.id,
        'COMPLETED',
        'DELIVERED',
        new Date(ended - HOUR).toISOString(),
        new Date(ended).toISOString(),
      );
    }
    for (let k = 0; k < sample.done; k++) {
      const ended = midnight - (k + 1) * 26 * HOUR;
      addAssignment(
        courier.id,
        'COMPLETED',
        'DELIVERED',
        new Date(ended - 2 * HOUR).toISOString(),
        new Date(ended).toISOString(),
      );
    }
    if (sample.done > 2) {
      addAssignment(courier.id, 'FAILED', 'FAILED_ATTEMPT', hoursAgo(80), hoursAgo(78));
      addAssignment(
        courier.id,
        'REASSIGNED',
        'IN_TRANSIT',
        hoursAgo(100),
        hoursAgo(99),
        'Vehicle broke down',
      );
    }
    for (let k = 0; k < inProgress; k++) {
      addAssignment(courier.id, 'IN_PROGRESS', ON_THE_WAY[k % 3], hoursAgo(2 + k), null);
    }
    for (let k = 0; k < assigned; k++) {
      addAssignment(courier.id, 'ASSIGNED', 'CREATED', hoursAgo(0.5 + k * 0.25), null);
    }
  });
}

seed();

/** Back to the sample data (tests). */
export function resetMockCouriers(): void {
  seed();
}

// ---- Rules (as in the backend's CourierService and CourierRules) --------------------------------

const PERSON_NAME = /^\p{L}[\p{L}\p{M}\s.'-]*$/u;
const PHONE_E164 = /^\+[1-9]\d{6,14}$/;
const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d)\S(.*\S)?$/;
const VEHICLE_REGISTRATION = /^[A-Za-z0-9][A-Za-z0-9 -]*$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const UNKNOWN_SERVICE_AREA = 'Unknown or inactive service area';

function normalizeRegistration(registration: string): string {
  return registration.trim().replace(/\s+/g, ' ').toUpperCase();
}

function notFound(): ApiRequestError {
  return new ApiRequestError('Courier not found', { status: 404, code: 'NOT_FOUND' });
}

function conflictOn(field: string, message: string): ApiRequestError {
  return new ApiRequestError(message, {
    status: 409,
    code: 'CONFLICT',
    fieldErrors: [{ field, message }],
  });
}

function validation(fieldErrors: { field: string; message: string }[]): ApiRequestError {
  return new ApiRequestError('Request validation failed', {
    status: 400,
    code: 'VALIDATION_FAILED',
    fieldErrors,
  });
}

function checkNewCourier(input: NewCourier) {
  const errors: { field: string; message: string }[] = [];
  const name = input.fullName?.trim() ?? '';
  if (name.length < 2 || name.length > 120)
    errors.push({ field: 'fullName', message: 'Full name must be 2-120 characters' });
  else if (!PERSON_NAME.test(name)) {
    errors.push({
      field: 'fullName',
      message: 'Full name can only contain letters, spaces, apostrophes, dots and hyphens',
    });
  }
  if (!EMAIL.test(input.email?.trim() ?? ''))
    errors.push({ field: 'email', message: 'Enter a valid email address' });
  if (!PHONE_E164.test(input.phone ?? ''))
    errors.push({ field: 'phone', message: 'Enter a valid phone number' });
  if ((input.password?.length ?? 0) < 8 || input.password.length > 72) {
    errors.push({ field: 'password', message: 'Password must be 8-72 characters' });
  } else if (!PASSWORD.test(input.password)) {
    errors.push({
      field: 'password',
      message:
        'Password must include at least one letter and one number and cannot start or end with a space',
    });
  }
  if (!input.vehicleType?.trim())
    errors.push({ field: 'vehicleType', message: 'Vehicle type is required' });
  if (!VEHICLE_REGISTRATION.test(input.vehicleRegistration?.trim() ?? '')) {
    errors.push({
      field: 'vehicleRegistration',
      message: 'Vehicle registration can only contain letters, numbers, spaces and hyphens',
    });
  }
  if (errors.length > 0) throw validation(errors);
}

function requireActiveZones(zoneIds: string[] | undefined) {
  if (zoneIds && zoneIds.length > 0 && mockMissingOrInactiveZones(zoneIds).length > 0) {
    throw new ApiRequestError(UNKNOWN_SERVICE_AREA, { status: 400, code: 'BAD_REQUEST' });
  }
}

function find(courierId: string): MockCourier {
  const found = couriers.find((c) => c.id === courierId);
  if (!found) throw notFound();
  return found;
}

// ---- Views ---------------------------------------------------------------------------------------

function workloadOf(courierId: string): CourierWorkload {
  const midnight = startOfToday();
  let assigned = 0;
  let inProgress = 0;
  let completedToday = 0;
  for (const a of assignments) {
    if (a.courierId !== courierId) continue;
    if (a.endedAt === null && a.status === 'ASSIGNED') assigned++;
    else if (a.endedAt === null && a.status === 'IN_PROGRESS') inProgress++;
    else if (a.status === 'COMPLETED' && a.endedAt && Date.parse(a.endedAt) >= midnight)
      completedToday++;
  }
  const open = assigned + inProgress;
  return { assigned, inProgress, completedToday, open, todaysTotal: open + completedToday };
}

function toCourier(c: MockCourier): Courier {
  const serviceAreas = c.serviceAreaIds
    .map((zoneId) => ({ id: zoneId, name: mockZoneName(zoneId) }))
    .sort((a, b) => (a.name ?? '￿').localeCompare(b.name ?? '￿'));
  return {
    id: c.id,
    userId: c.userId,
    fullName: c.fullName,
    email: c.email,
    phone: c.phone,
    vehicleType: c.vehicleType,
    vehicleRegistration: c.vehicleRegistration,
    availability: c.availability,
    active: c.active,
    accountStatus: c.accountStatus,
    serviceAreaIds: [...c.serviceAreaIds],
    serviceAreas,
    workload: workloadOf(c.id),
    createdAt: c.createdAt,
  };
}

/** An assignment as the API returns it (without the courier id the mock keeps). */
function toRow(a: MockAssignment): CourierAssignmentRow {
  return {
    id: a.id,
    parcelId: a.parcelId,
    status: a.status,
    assignedAt: a.assignedAt,
    endedAt: a.endedAt,
    reason: a.reason,
    trackingNumber: a.trackingNumber,
    parcelStatus: a.parcelStatus,
    destinationCity: a.destinationCity,
  };
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

// ---- API -----------------------------------------------------------------------------------------

export const mockCouriersApi: CouriersApi = {
  async list({ search, availability, serviceAreaId, vehicleType, active, page: pageIndex, size }) {
    await delay(250);
    const text = search?.trim().toLowerCase();
    const found = couriers
      .filter((c) => !availability || c.availability === availability)
      .filter((c) => !serviceAreaId || c.serviceAreaIds.includes(serviceAreaId))
      .filter(
        (c) => !vehicleType || c.vehicleType.toLowerCase() === vehicleType.trim().toLowerCase(),
      )
      .filter((c) => active === undefined || c.active === active)
      .filter(
        (c) =>
          !text ||
          [c.fullName, c.email, c.phone, c.vehicleRegistration].some((v) =>
            v.toLowerCase().includes(text),
          ),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return page(found.map(toCourier), pageIndex ?? 0, size ?? 20);
  },

  async summary() {
    await delay(200);
    const active = couriers.filter((c) => c.active);
    const count = (availability: CourierAvailability) =>
      active.filter((c) => c.availability === availability).length;
    return {
      total: active.length,
      online: count('ONLINE'),
      onBreak: count('ON_BREAK'),
      offline: count('OFFLINE'),
    };
  },

  async get(courierId) {
    await delay(200);
    return toCourier(find(courierId));
  },

  async create(input) {
    await delay(300);
    checkNewCourier(input);
    const email = input.email.trim();
    if (couriers.some((c) => c.email.toLowerCase() === email.toLowerCase())) {
      throw conflictOn('email', 'An account with this email already exists');
    }
    const registration = normalizeRegistration(input.vehicleRegistration);
    if (couriers.some((c) => c.vehicleRegistration === registration)) {
      throw conflictOn(
        'vehicleRegistration',
        'A courier with this vehicle registration already exists',
      );
    }
    requireActiveZones(input.serviceAreaIds);
    const created: MockCourier = {
      id: uuid('c0010000'),
      userId: uuid('05e10000'),
      fullName: input.fullName.trim(),
      email,
      phone: input.phone,
      vehicleType: input.vehicleType.trim(),
      vehicleRegistration: registration,
      availability: 'OFFLINE',
      active: true,
      accountStatus: 'ACTIVE',
      serviceAreaIds: [...new Set(input.serviceAreaIds ?? [])],
      createdAt: new Date().toISOString(),
    };
    couriers.push(created);
    return toCourier(created);
  },

  async update(courierId, input) {
    await delay(250);
    if (
      input.vehicleType === undefined &&
      input.vehicleRegistration === undefined &&
      input.serviceAreaIds === undefined
    ) {
      throw new ApiRequestError('Nothing to update', { status: 400, code: 'BAD_REQUEST' });
    }
    const target = find(courierId);
    if (input.vehicleRegistration !== undefined) {
      if (!VEHICLE_REGISTRATION.test(input.vehicleRegistration.trim())) {
        throw validation([
          {
            field: 'vehicleRegistration',
            message: 'Vehicle registration can only contain letters, numbers, spaces and hyphens',
          },
        ]);
      }
      const registration = normalizeRegistration(input.vehicleRegistration);
      if (couriers.some((c) => c.id !== courierId && c.vehicleRegistration === registration)) {
        throw conflictOn(
          'vehicleRegistration',
          'Another courier already has this vehicle registration',
        );
      }
      target.vehicleRegistration = registration;
    }
    if (input.serviceAreaIds !== undefined) {
      requireActiveZones(input.serviceAreaIds);
      target.serviceAreaIds = [...new Set(input.serviceAreaIds)];
    }
    if (input.vehicleType !== undefined) target.vehicleType = input.vehicleType.trim();
    return toCourier(target);
  },

  async changeAvailability(courierId, availability) {
    await delay(200);
    const target = find(courierId);
    if (availability === 'ONLINE' && (!target.active || target.accountStatus !== 'ACTIVE')) {
      throw new ApiRequestError('This courier is deactivated and cannot go online', {
        status: 409,
        code: 'CONFLICT',
      });
    }
    target.availability = availability;
    return toCourier(target);
  },

  async listAssignments(courierId, { open, page: pageIndex, size }) {
    await delay(200);
    find(courierId);
    const found = assignments
      .filter((a) => a.courierId === courierId)
      .filter((a) => open === undefined || (a.endedAt === null) === open)
      .sort((a, b) => b.assignedAt.localeCompare(a.assignedAt))
      .map(toRow);
    return page(found, pageIndex ?? 0, size ?? 20);
  },
};
