import type { AssignmentStatus } from '../../types/courier';
import type {
  ActiveDelivery,
  Assignment,
  BulkAssignFailure,
  DispatchApi,
  FailedDelivery,
} from '../../types/dispatch';
import { MAX_BULK_PARCELS, MAX_FAILED_PAGE_SIZE, MAX_REASON_LENGTH } from '../../types/dispatch';
import type { AdminParcelRow, Paged } from '../../types/parcel';
import type { FailedDeliveryAction } from '../../types/settings';
import type { ParcelStatusCode } from '../../types/tracking';
import { ApiRequestError } from '../errors';
import { delay } from './delay';
import {
  mockAddAssignment,
  mockAllAssignments,
  mockAllCouriers,
  mockAssignmentById,
  mockCourierById,
  mockCouriersApi,
  mockOpenAssignment,
  mockSriLankaMidnight,
  type MockAssignment,
} from './mockCouriersApi';
import { mockParcelsApi, setMockDispatchState, setMockParcelStatus } from './mockParcelsApi';

/**
 * Assign, reassign and unassign on the sample data (VITE_USE_MOCK_API, and every Jest test), with
 * the backend's rules and messages (AssignmentService): the parcel must be waiting (CREATED) without
 * a courier, the courier must exist, be active, online and have an active account, a parcel can't be
 * reassigned to the courier it already has, unassign only before pickup, and bulk reports each parcel.
 *
 * Not modelled: the pickup-area check (the sample parcels have no pickup zone).
 */

const ADMIN_USER_ID = 'ad000000-0000-4000-8000-000000000001';
const WITH_COURIER: ParcelStatusCode[] = ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'];
const FINAL: ParcelStatusCode[] = ['DELIVERED', 'FAILED'];
const ALREADY_ASSIGNED = 'This parcel is already assigned to a courier';

function conflict(message: string): ApiRequestError {
  return new ApiRequestError(message, { status: 409, code: 'CONFLICT' });
}

function notFound(message: string): ApiRequestError {
  return new ApiRequestError(message, { status: 404, code: 'NOT_FOUND' });
}

function invalid(field: string, message: string): ApiRequestError {
  return new ApiRequestError('Request validation failed', {
    status: 400,
    code: 'VALIDATION_FAILED',
    fieldErrors: [{ field, message }],
  });
}

/** Every admin sample parcel, by id (one simulated request). */
async function parcelsById(): Promise<Map<string, AdminParcelRow>> {
  const all = await mockParcelsApi.listAdminParcels({ size: 10_000 });
  return new Map(all.content.map((parcel) => [parcel.id, parcel]));
}

/** As AssignmentService.assignableCourier (without the pickup-area check). */
function assignableCourier(courierId: string) {
  const courier = mockCourierById(courierId);
  if (!courier) throw notFound('Courier not found');
  if (!courier.active) throw conflict('This courier is deactivated and cannot be given parcels');
  if (courier.availability !== 'ONLINE') {
    throw conflict(
      courier.availability === 'ON_BREAK'
        ? 'This courier is on a break and cannot be given parcels'
        : 'This courier is offline and cannot be given parcels',
    );
  }
  if (courier.accountStatus !== 'ACTIVE') {
    throw conflict("This courier's account is not active, so they cannot be given parcels");
  }
  return courier;
}

function toAssignment(a: MockAssignment): Assignment {
  return {
    id: a.id,
    parcelId: a.parcelId,
    courierId: a.courierId,
    courierUserId: mockCourierById(a.courierId)?.userId ?? '',
    status: a.status,
    assignedByUserId: ADMIN_USER_ID,
    assignedAt: a.assignedAt,
    endedAt: a.endedAt,
    reason: a.reason,
  };
}

function end(a: MockAssignment, status: AssignmentStatus, reason: string | null) {
  a.status = status;
  a.endedAt = new Date().toISOString();
  a.reason = reason;
}

function assignOne(parcels: Map<string, AdminParcelRow>, parcelId: string, courierId: string) {
  const parcel = parcels.get(parcelId);
  if (!parcel) throw notFound('Parcel not found');
  if (parcel.status !== 'CREATED') {
    throw conflict('Only a parcel that has not been picked up yet can be assigned');
  }
  if (parcel.dispatchState === 'ASSIGNED' || mockOpenAssignment(parcelId)) {
    throw conflict(ALREADY_ASSIGNED);
  }
  assignableCourier(courierId);
  const created = mockAddAssignment({
    courierId,
    parcelId,
    status: 'ASSIGNED',
    trackingNumber: parcel.trackingNumber,
    parcelStatus: parcel.status,
    destinationCity: parcel.destination,
  });
  setMockDispatchState(parcelId, 'ASSIGNED');
  parcel.dispatchState = 'ASSIGNED';
  return toAssignment(created);
}

function checkReason(reason: string | undefined, required: boolean, field = 'reason') {
  const label = field === 'note' ? 'Note' : 'Reason';
  if (required && !reason?.trim()) throw invalid(field, `${label} is required`);
  if ((reason?.length ?? 0) > MAX_REASON_LENGTH) {
    throw invalid(field, `${label} must be at most ${MAX_REASON_LENGTH} characters`);
  }
}

// ---- Failed deliveries and the overview (FailedDeliveryService, OperationsService) -------------

const NOT_FAILED_ATTEMPT = 'Only a parcel whose delivery attempt failed can be retried or closed';
let defaultAction: FailedDeliveryAction = 'RETRY_DELIVERY';
/** Close reasons and times recorded by close(); the sample history doesn't change. */
const closedHere = new Map<string, { reason: string; at: string }>();

/** The System Settings default the mock reports (tests). */
export function setMockDefaultFailedAction(action: FailedDeliveryAction): void {
  defaultAction = action;
}

/** Back to the default action and no closes (tests; call with resetMockDispatchStates). */
export function resetMockFailedDeliveries(): void {
  defaultAction = 'RETRY_DELIVERY';
  closedHere.clear();
}

function checkPage(page: number, size: number) {
  if (page < 0) {
    throw new ApiRequestError('page must be 0 or more', { status: 400, code: 'BAD_REQUEST' });
  }
  if (size < 1 || size > MAX_FAILED_PAGE_SIZE) {
    throw new ApiRequestError(`size must be between 1 and ${MAX_FAILED_PAGE_SIZE}`, {
      status: 400,
      code: 'BAD_REQUEST',
    });
  }
}

function pageOf<T>(items: T[], page: number, size: number): Paged<T> {
  return {
    content: items.slice(page * size, page * size + size),
    page: {
      size,
      number: page,
      totalElements: items.length,
      totalPages: Math.ceil(items.length / size),
    },
  };
}

/** Parcels in FAILED_ATTEMPT (ATTENTION) or FAILED (CLOSED), newest booking first. */
async function failedRows(scope: 'ATTENTION' | 'CLOSED'): Promise<FailedDelivery[]> {
  const wanted: ParcelStatusCode = scope === 'ATTENTION' ? 'FAILED_ATTEMPT' : 'FAILED';
  const rows = (await mockParcelsApi.listAdminParcels({ tab: 'FAILED', size: 10_000 })).content
    .filter((p) => p.status === wanted)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const details = await Promise.all(rows.map((p) => mockParcelsApi.getAdminParcel(p.id)));
  // The sample parcels record no courier: give each a sample courier (stable per parcel).
  const couriers = mockAllCouriers().filter((c) => c.active);
  return rows.map((parcel, i) => {
    const entry = details[i]?.history
      .filter((h) => h.status === 'FAILED_ATTEMPT' || h.status === 'FAILED')
      .at(-1);
    const closed = closedHere.get(parcel.id);
    const digit = Number(parcel.trackingNumber.slice(-1)) || 0;
    const courier = couriers[digit % couriers.length];
    return {
      parcelId: parcel.id,
      trackingNumber: parcel.trackingNumber,
      parcelStatus: parcel.status,
      failureReason: closed?.reason ?? entry?.failureReason ?? null,
      failedAt: closed?.at ?? entry?.changedAt ?? null,
      courierId: courier?.id ?? null,
      courierName: courier?.fullName ?? null,
      attemptCount: 1 + (digit % 2),
      destinationCity: parcel.destination,
    };
  });
}

async function failedAttempt(parcelId: string): Promise<AdminParcelRow> {
  const parcel = (await parcelsById()).get(parcelId);
  if (!parcel) throw notFound('Parcel not found');
  if (parcel.status !== 'FAILED_ATTEMPT') throw conflict(NOT_FAILED_ATTEMPT);
  return parcel;
}

function activeRows(): ActiveDelivery[] {
  return mockAllAssignments()
    .filter((a) => a.endedAt === null && a.status === 'IN_PROGRESS')
    .sort((a, b) => b.assignedAt.localeCompare(a.assignedAt))
    .map((a) => ({
      parcelId: a.parcelId,
      trackingNumber: a.trackingNumber,
      courierId: a.courierId,
      courierName: mockCourierById(a.courierId)?.fullName ?? null,
      destinationCity: a.destinationCity,
      parcelStatus: a.parcelStatus,
      assignedAt: a.assignedAt,
    }));
}

export const mockDispatchApi: DispatchApi = {
  async assign(parcelId, courierId) {
    return assignOne(await parcelsById(), parcelId, courierId);
  },

  async assignAll(courierId, parcelIds) {
    if (parcelIds.length === 0) throw invalid('parcelIds', 'Choose at least one parcel');
    if (parcelIds.length > MAX_BULK_PARCELS) {
      throw invalid('parcelIds', `At most ${MAX_BULK_PARCELS} parcels can be assigned at once`);
    }
    const parcels = await parcelsById();
    const assigned: Assignment[] = [];
    const failed: BulkAssignFailure[] = [];
    for (const parcelId of new Set(parcelIds)) {
      try {
        assigned.push(assignOne(parcels, parcelId, courierId));
      } catch (err) {
        failed.push({
          parcelId,
          reason: err instanceof ApiRequestError ? err.message : 'The parcel could not be assigned',
        });
      }
    }
    return { assigned, failed };
  },

  async reassign(parcelId, courierId, reason) {
    checkReason(reason, true);
    const parcels = await parcelsById();
    const current = mockOpenAssignment(parcelId);
    const status = parcels.get(parcelId)?.status ?? current?.parcelStatus;
    if (!status) throw notFound('Parcel not found');
    if (FINAL.includes(status)) throw conflict('A delivered or failed parcel cannot be reassigned');
    if (!current) throw conflict('This parcel has no courier yet: assign it instead');
    if (current.courierId === courierId) {
      throw conflict('This parcel is already assigned to this courier');
    }
    assignableCourier(courierId);
    end(current, 'REASSIGNED', reason.trim());
    const created = mockAddAssignment({
      courierId,
      parcelId,
      status: WITH_COURIER.includes(status) ? 'IN_PROGRESS' : 'ASSIGNED',
      trackingNumber: current.trackingNumber,
      parcelStatus: current.parcelStatus,
      destinationCity: current.destinationCity,
    });
    return toAssignment(created);
  },

  async unassign(parcelId, reason) {
    checkReason(reason, false);
    const parcels = await parcelsById();
    const current = mockOpenAssignment(parcelId);
    const status = parcels.get(parcelId)?.status ?? current?.parcelStatus;
    if (!status) throw notFound('Parcel not found');
    if (status !== 'CREATED') {
      throw conflict('Only a parcel that has not been picked up yet can be unassigned');
    }
    if (!current) throw conflict('This parcel is not assigned to a courier');
    end(current, 'CANCELLED', reason?.trim() || null);
    if (parcels.has(parcelId)) setMockDispatchState(parcelId, 'UNASSIGNED');
    return toAssignment(current);
  },

  async getAssignment(assignmentId) {
    await delay(150);
    const found = mockAssignmentById(assignmentId);
    if (!found) throw notFound('Assignment not found');
    return toAssignment(found);
  },

  async listFailed({ scope, page = 0, size = 20 }) {
    checkPage(page, size);
    return pageOf(await failedRows(scope), page, size);
  },

  async defaultFailedAction() {
    await delay(150);
    return defaultAction;
  },

  async retry(parcelId, note) {
    checkReason(note, false, 'note');
    await failedAttempt(parcelId);
    // Back to CREATED and waiting for a courier: the Unassigned tab shows it again.
    setMockParcelStatus(parcelId, 'CREATED');
    setMockDispatchState(parcelId, 'UNASSIGNED');
    return { parcelId, status: 'CREATED' };
  },

  async close(parcelId, reason) {
    checkReason(reason, true);
    await failedAttempt(parcelId);
    setMockParcelStatus(parcelId, 'FAILED');
    closedHere.set(parcelId, { reason: reason.trim(), at: new Date().toISOString() });
    return { parcelId, status: 'FAILED' };
  },

  async overview() {
    const [counts, summary, couriers, attention] = await Promise.all([
      mockParcelsApi.getAdminCounts(),
      mockCouriersApi.summary(),
      mockCouriersApi.list({ active: true, size: 100 }),
      failedRows('ATTENTION'),
    ]);
    const midnight = mockSriLankaMidnight();
    const endedToday = (status: AssignmentStatus) =>
      mockAllAssignments().filter(
        (a) => a.status === status && a.endedAt !== null && Date.parse(a.endedAt) >= midnight,
      ).length;
    const active = activeRows();
    const outForDelivery = active.filter((a) => a.parcelStatus === 'OUT_FOR_DELIVERY').length;
    const completedToday = endedToday('COMPLETED');
    const failedToday = endedToday('FAILED');
    return {
      date: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo' }).format(new Date()),
      defaultAction,
      kpis: {
        unassignedParcels: counts.UNASSIGNED,
        activeDeliveries: active.length,
        couriersOnline: summary.online,
        couriersOnBreak: summary.onBreak,
        couriersTotal: summary.total,
        completedToday,
        failedToday,
        needsDecision: attention.length,
      },
      todayStatus: {
        completed: completedToday,
        inTransit: active.length - outForDelivery,
        outForDelivery,
        failed: failedToday,
      },
      courierWorkload: [...couriers.content]
        .sort(
          (a, b) =>
            b.workload.todaysTotal - a.workload.todaysTotal || a.fullName.localeCompare(b.fullName),
        )
        .slice(0, 5)
        .map((c) => ({
          courierId: c.id,
          name: c.fullName,
          todaysTotal: c.workload.todaysTotal,
          completed: c.workload.completedToday,
          inProgress: c.workload.inProgress,
          pending: c.workload.assigned,
          availability: c.availability,
        })),
    };
  },

  async activeDeliveries({ page = 0, size = 20 }) {
    checkPage(page, size);
    await delay(250);
    return pageOf(activeRows(), page, size);
  },
};
