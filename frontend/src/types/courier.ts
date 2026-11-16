import type { Paged } from './parcel';
import type { ParcelStatusCode } from './tracking';

/** Backend CourierAvailability (V6_001). */
export type CourierAvailability = 'ONLINE' | 'ON_BREAK' | 'OFFLINE';

/** Backend user AccountStatus of the courier's sign-in account. */
export type CourierAccountStatus = 'ACTIVE' | 'PENDING_APPROVAL' | 'SUSPENDED' | 'DEACTIVATED';

/** Backend AssignmentStatus. ASSIGNED and IN_PROGRESS are open; the others are end states. */
export type AssignmentStatus =
  'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'REASSIGNED' | 'CANCELLED';

/** Today's work (CourierWorkload). The design's "12 / 15" is completedToday / todaysTotal. */
export interface CourierWorkload {
  /** open, not started */
  assigned: number;
  /** open, being delivered */
  inProgress: number;
  /** completed since midnight, Sri Lanka time */
  completedToday: number;
  open: number;
  todaysTotal: number;
}

/** A service area (delivery zone) a courier covers (ServiceAreaRef). */
export interface CourierServiceArea {
  id: string;
  /** null if the zone can't be read */
  name: string | null;
}

/** GET /admin/couriers rows and detail (CourierResponse). */
export interface Courier {
  id: string;
  /** The courier's sign-in account (users.user_id). */
  userId: string;
  fullName: string;
  email: string;
  phone: string;
  vehicleType: string;
  vehicleRegistration: string;
  availability: CourierAvailability;
  /** false once deactivated */
  active: boolean;
  accountStatus: CourierAccountStatus;
  serviceAreaIds: string[];
  /** The same zones with their names, sorted by name. */
  serviceAreas: CourierServiceArea[];
  workload: CourierWorkload;
  /** ISO timestamp */
  createdAt: string;
}

/** GET /admin/couriers/summary (CourierSummaryResponse): active couriers only. */
export interface CourierSummary {
  total: number;
  online: number;
  onBreak: number;
  offline: number;
}

/** GET /admin/couriers/{id}/assignments rows (CourierAssignmentResponse). */
export interface CourierAssignmentRow {
  id: string;
  parcelId: string;
  status: AssignmentStatus;
  assignedAt: string;
  /** null while open */
  endedAt: string | null;
  /** why it was reassigned or cancelled, if given */
  reason: string | null;
  /** parcel fields are null if the parcel can't be read */
  trackingNumber: string | null;
  parcelStatus: ParcelStatusCode | null;
  destinationCity: string | null;
}

export interface CourierFilters {
  search?: string;
  availability?: CourierAvailability;
  serviceAreaId?: string;
  vehicleType?: string;
  active?: boolean;
  page?: number;
  /** 1-100 */
  size?: number;
}

/** POST /admin/couriers (CreateCourierRequest). */
export interface NewCourier {
  fullName: string;
  email: string;
  /** E.164, e.g. +94771234567 */
  phone: string;
  password: string;
  vehicleType: string;
  vehicleRegistration: string;
  serviceAreaIds?: string[];
}

/** PATCH /admin/couriers/{id} (UpdateCourierRequest): fields left out stay as they are. */
export interface CourierUpdate {
  vehicleType?: string;
  vehicleRegistration?: string;
  /** Replaces the whole set; [] clears it. Every id must be an active zone. */
  serviceAreaIds?: string[];
}

export interface CouriersApi {
  list(filters: CourierFilters): Promise<Paged<Courier>>;
  summary(): Promise<CourierSummary>;
  get(courierId: string): Promise<Courier>;
  create(courier: NewCourier): Promise<Courier>;
  update(courierId: string, update: CourierUpdate): Promise<Courier>;
  changeAvailability(courierId: string, availability: CourierAvailability): Promise<Courier>;
  /** open: true current deliveries, false history, undefined all. Newest first. */
  listAssignments(
    courierId: string,
    query: { open?: boolean; page?: number; size?: number },
  ): Promise<Paged<CourierAssignmentRow>>;
}
