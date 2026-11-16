import type { AssignmentStatus, CourierAvailability } from './courier';
import type { Paged } from './parcel';
import type { FailedDeliveryAction } from './settings';
import type { ParcelStatusCode } from './tracking';

/** BulkAssignRequest: at most this many parcels per request. */
export const MAX_BULK_PARCELS = 50;

/** ReassignParcelRequest / UnassignParcelRequest: longest reason. */
export const MAX_REASON_LENGTH = 500;

/** One delivery assignment (AssignmentResponse). */
export interface Assignment {
  id: string;
  parcelId: string;
  courierId: string;
  /** The courier's sign-in account (users.user_id). */
  courierUserId: string;
  status: AssignmentStatus;
  /** The dispatcher who made it. */
  assignedByUserId: string | null;
  assignedAt: string;
  /** null while open */
  endedAt: string | null;
  /** why it was reassigned or cancelled, if given */
  reason: string | null;
}

/** A parcel the bulk request could not assign, with the backend's reason. */
export interface BulkAssignFailure {
  parcelId: string;
  reason: string;
}

/** POST /admin/dispatch/assignments/bulk (BulkAssignResponse): 200 even when every parcel failed. */
export interface BulkAssignResult {
  assigned: Assignment[];
  failed: BulkAssignFailure[];
}

/** GET /failed-deliveries: page size 1-50. */
export const MAX_FAILED_PAGE_SIZE = 50;

/** ATTENTION: FAILED_ATTEMPT parcels waiting for a decision; CLOSED: parcels closed as FAILED. */
export type FailedScope = 'ATTENTION' | 'CLOSED';

/** A row of the Failed Deliveries queue (FailedDeliveryResponse). */
export interface FailedDelivery {
  parcelId: string;
  trackingNumber: string;
  /** FAILED_ATTEMPT (needs a decision) or FAILED (closed) */
  parcelStatus: ParcelStatusCode;
  /** From the latest FAILED_ATTEMPT / FAILED history entry (the close reason once closed). */
  failureReason: string | null;
  failedAt: string | null;
  /** Courier of the most recent failed attempt; null if none is recorded. */
  courierId: string | null;
  courierName: string | null;
  /** Failed attempts of this parcel. */
  attemptCount: number;
  destinationCity: string | null;
}

/** The parcel after a retry (CREATED) or a close (FAILED) (FailedDecisionResponse). */
export interface FailedDecision {
  parcelId: string;
  status: ParcelStatusCode;
}

/** A delivery under way: an open IN_PROGRESS assignment (ActiveDeliveryResponse). No ETA. */
export interface ActiveDelivery {
  parcelId: string;
  trackingNumber: string | null;
  courierId: string;
  courierName: string | null;
  destinationCity: string | null;
  /** PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY */
  parcelStatus: ParcelStatusCode | null;
  assignedAt: string;
}

/** GET /overview (OverviewResponse). "Today" is Sri Lanka time; definitions in docs/dispatch.md. */
export interface Overview {
  /** yyyy-mm-dd */
  date: string;
  defaultAction: FailedDeliveryAction;
  kpis: {
    unassignedParcels: number;
    activeDeliveries: number;
    couriersOnline: number;
    couriersOnBreak: number;
    couriersTotal: number;
    completedToday: number;
    failedToday: number;
    needsDecision: number;
  };
  todayStatus: {
    completed: number;
    /** PICKED_UP + IN_TRANSIT */
    inTransit: number;
    outForDelivery: number;
    failed: number;
  };
  /** At most 5 active couriers, busiest first. */
  courierWorkload: {
    courierId: string;
    name: string | null;
    todaysTotal: number;
    completed: number;
    inProgress: number;
    /** open, not started (ASSIGNED) */
    pending: number;
    availability: CourierAvailability;
  }[];
}

/** The admin dispatch API (dispatch module, DISPATCHER_ADMIN). See docs/dispatch.md. */
export interface DispatchApi {
  listFailed(query: {
    scope: FailedScope;
    page?: number;
    size?: number;
  }): Promise<Paged<FailedDelivery>>;
  /** The System Settings default, to emphasise Retry or pre-fill the close reason. */
  defaultFailedAction(): Promise<FailedDeliveryAction>;
  /** FAILED_ATTEMPT -> CREATED (back in Unassigned); the note goes into the history. */
  retry(parcelId: string, note?: string): Promise<FailedDecision>;
  /** FAILED_ATTEMPT -> FAILED (end state) with the reason. */
  close(parcelId: string, reason: string): Promise<FailedDecision>;
  overview(): Promise<Overview>;
  activeDeliveries(query: { page?: number; size?: number }): Promise<Paged<ActiveDelivery>>;

  /** Gives a waiting (CREATED) parcel to an online courier. */
  assign(parcelId: string, courierId: string): Promise<Assignment>;
  /** Gives 1-50 parcels to one courier; each parcel succeeds or fails on its own. */
  assignAll(courierId: string, parcelIds: string[]): Promise<BulkAssignResult>;
  /** Moves the parcel to another courier; returns the new assignment. */
  reassign(parcelId: string, courierId: string, reason: string): Promise<Assignment>;
  /** Takes the parcel back before pickup; returns the ended assignment. */
  unassign(parcelId: string, reason?: string): Promise<Assignment>;
  getAssignment(assignmentId: string): Promise<Assignment>;
}
