import type { AssignmentStatus } from './courier';

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

/** The admin dispatch API (dispatch module, DISPATCHER_ADMIN). See docs/dispatch.md. */
export interface DispatchApi {
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
