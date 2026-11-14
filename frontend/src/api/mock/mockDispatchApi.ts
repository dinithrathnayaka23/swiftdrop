import type { AssignmentStatus } from '../../types/courier';
import type { Assignment, BulkAssignFailure, DispatchApi } from '../../types/dispatch';
import { MAX_BULK_PARCELS, MAX_REASON_LENGTH } from '../../types/dispatch';
import type { AdminParcelRow } from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';
import { ApiRequestError } from '../errors';
import { delay } from './delay';
import {
  mockAddAssignment,
  mockAssignmentById,
  mockCourierById,
  mockOpenAssignment,
  type MockAssignment,
} from './mockCouriersApi';
import { mockParcelsApi, setMockDispatchState } from './mockParcelsApi';

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

function checkReason(reason: string | undefined, required: boolean) {
  if (required && !reason?.trim()) throw invalid('reason', 'Reason is required');
  if ((reason?.length ?? 0) > MAX_REASON_LENGTH) {
    throw invalid('reason', `Reason must be at most ${MAX_REASON_LENGTH} characters`);
  }
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
};
