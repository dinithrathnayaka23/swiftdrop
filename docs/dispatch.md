# Dispatch module

Owner: Member 6. Package `com.swiftdrop.dispatch`; frontend folders `dispatch` and `couriers`.

The dispatch module owns couriers (profile, vehicle, service areas, availability) and the
assignment of parcels to couriers. A courier is a `COURIER` account (user module) plus a profile row
(`couriers`) owned by this module.

> **Status:** courier management and assigning parcels (single, bulk, reassign, unassign) are built,
> with the service-area check, and assignments follow the parcel's status. The Operations Overview data and the failed-delivery
> queue are not (see "Not done yet").

## For the parcel module: `DispatchStateLookup`

`AssignmentLookupService` is the `DispatchStateLookup` bean the parcel module asked for, so the admin
Parcels tabs, counts and each row's `dispatchState` now tell Assigned from Unassigned.

## For the courier app and other modules: `DispatchApi`

Use `com.swiftdrop.dispatch.api.DispatchApi`; never the dispatch repositories or entities. A courier is
identified by their **account id** (`AuthenticatedUser.userId()`).

| Method | Use it for |
| ------ | ---------- |
| `isAssignedTo(parcelId, courierUserId)` | The ownership check before a courier reads or changes a parcel. |
| `openParcelIds(courierUserId)` | The courier's Today's Deliveries (oldest assignment first); load them with `ParcelService.findAllByIds`. |
| `findCourierId(courierUserId)` | The courier profile id, or empty if the account has none. |

## For live tracking: `CourierLookup`

`CourierNameLookupService` is the tracking module's `CourierLookup` bean, so the admin Deliveries
Tracking board shows the courier's name. `currentCourierNames(parcelIds)` finds the open assignments of
all the parcels in one query and reads each distinct courier's account once; parcels without an open
assignment are left out.

## Assignment lifecycle (who ends assignments, and when)

An assignment is **open** while `delivery_assignments.ended_at IS NULL`; the database allows one open
assignment per parcel (`ux_delivery_assignments_open_per_parcel`). The Assigned tab, the courier app's
ownership check and the workload counts all rely on it.

**Only the dispatch module changes assignments.** Other modules (the courier app included) change the
*parcel's* status with `ParcelStatusService`; dispatch hears about it and updates the assignment.

```
           assign                    PICKED_UP / IN_TRANSIT /
 (none) ──────────▶ ASSIGNED ──────── OUT_FOR_DELIVERY event ──────▶ IN_PROGRESS
                      │  │                                             │  │
     unassign (admin) │  │ reassign (admin)          DELIVERED event   │  │ FAILED event
                      ▼  ▼                                             ▼  ▼
               CANCELLED  REASSIGNED  (+ a new open assignment)  COMPLETED  FAILED
```

| Ended by | When | Status | `ended_at` |
| -------- | ---- | ------ | ---------- |
| Admin: unassign | Parcel still `CREATED` | `CANCELLED` | now |
| Admin: reassign | Parcel not `DELIVERED` / `FAILED` | `REASSIGNED` (+ reason) | now |
| Status listener | Parcel became `DELIVERED` | `COMPLETED` | the event's `occurredAt` |
| Status listener | Parcel became `FAILED` | `FAILED` (V6_002; never counts as completed) | the event's `occurredAt` |

- Assigning **does not change the parcel's status**: it stays `CREATED` until the courier picks it up.
- A parcel reassigned after pickup (`PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`) starts `IN_PROGRESS`
  with the new courier, because no new pickup event will come.
- `ended_at` is never earlier than `assigned_at` (the database checks it).

### The status listener

`dispatch.event.AssignmentStatusListener` reads `ParcelStatusChangedEvent` from its own **durable**
queue `swiftdrop.dispatch.parcel-events`, bound to `swiftdrop.parcel.events` with `parcel.status.#`
(`dispatch.event.DispatchQueueConfig`). `AssignmentLifecycleService` applies it to the parcel's open
assignment, row-locked so it can't overlap a reassignment:

| Parcel status | Open assignment |
| ------------- | --------------- |
| `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY` | `ASSIGNED` → `IN_PROGRESS` (already `IN_PROGRESS`: unchanged) |
| `DELIVERED` | → `COMPLETED`, `ended_at` set |
| `FAILED` | → `FAILED`, `ended_at` set |
| anything else (`CREATED`, unknown codes) | ignored (debug log) |

- **Idempotent:** delivery is at least once and out of order. The listener only moves an open
  assignment forward, and does nothing if the parcel has no open assignment, so duplicates and late
  events are harmless; no event-id store is needed.
- **Incomplete events** (no `parcelId` or `newStatus`) are skipped with a warning.
- **Errors:** database errors are thrown, so the message is retried by the shared listener settings in
  `application.yml` (`spring.rabbitmq.listener.simple`): `retry.max-attempts: 3`,
  `retry.initial-interval: 1s`, `retry.multiplier: 2` (attempts at 0 s, +1 s, +2 s), then, because
  `default-requeue-rejected: false`, the message is dead-lettered to `swiftdrop.parcel.events.dlx`
  (routing key `dead`) → `swiftdrop.parcel.events.dlq`, like the notification and webhook queues.

## Assignments API (`DISPATCHER_ADMIN` only)

All under `/api/v1/admin/dispatch`. The dispatcher who makes a change is taken from the sign-in token
(`assigned_by_user_id`).

| Endpoint | Notes |
| -------- | ----- |
| `POST /assignments` `{parcelId, courierId}` | 201 + `Location` + the assignment. Errors below. |
| `POST /assignments/bulk` `{courierId, parcelIds}` | 1-50 parcels, each in its own transaction. **Always 200**, even if every parcel failed: `{"assigned": [assignment...], "failed": [{"parcelId", "reason"}...]}`. A repeated id is handled once. |
| `GET /assignments/{assignmentId}` | One assignment (the `Location` above). |
| `POST /parcels/{parcelId}/reassign` `{courierId, reason}` | `reason` required, max 500. Ends the open assignment as `REASSIGNED` and returns the new one. |
| `POST /parcels/{parcelId}/unassign` `{reason?}` | Body optional; `reason` max 500. Returns the ended (`CANCELLED`) assignment. |

An assignment is `{id, parcelId, courierId, courierUserId, status, assignedByUserId, assignedAt, endedAt, reason}`.

| Rule | Error |
| ---- | ----- |
| Parcel exists | 404 |
| Assign / unassign: parcel is `CREATED` | 409 |
| Reassign: parcel is not `DELIVERED` / `FAILED` | 409 |
| Assign: no open assignment (also when two admins race: the unique index answers the same 409) | 409 |
| Reassign / unassign: there is an open assignment | 409 |
| Reassign: a different courier than the current one | 409 |
| Courier exists | 404 |
| Courier is active, `ONLINE` (not On Break / Offline) and their account is `ACTIVE` | 409 |
| Courier covers the parcel's **pickup** area (see below) | 409 `This courier does not cover the pickup area (<zone name>)` |

**Zone check** (assign, each parcel of a bulk assignment, and reassign): a courier **with** service
areas must cover the parcel's pickup zone. The pickup zone is the pickup address's `zoneId`, or, when
that is empty (today always: booking doesn't store it yet), `ZoneApi.findZoneId(district, city,
postalCode)` of the pickup address. A courier **without** service areas may take any parcel, and a
parcel whose pickup zone can't be found (outside every active zone) isn't checked. See `docs/config.md`
for how places are matched.

There is no "unassigned parcels" endpoint here: the screen uses `GET /api/v1/admin/parcels?tab=UNASSIGNED`
(parcel module).

## Couriers API (`DISPATCHER_ADMIN` only)

All under `/api/v1/admin/couriers`. Lists use Spring's paged JSON (`content`, `page`), newest first,
`size` 1-100 (default 20).

| Endpoint | Notes |
| -------- | ----- |
| `GET /?search&availability&serviceAreaId&vehicleType&active&page&size` | `search` matches name, email, phone or vehicle registration. Every row has today's `workload`. |
| `GET /summary` | `total`, `online`, `onBreak`, `offline` (active couriers only) |
| `GET /{courierId}` | One courier |
| `GET /{courierId}/assignments?open&page&size` | The drawer tabs: `open=true` "Current Deliveries", `open=false` "History", left out for all. Newest assignment first. Each row: `id, parcelId, status, assignedAt, endedAt, reason, trackingNumber, parcelStatus, destinationCity`. 404 for an unknown courier. |
| `POST /` | Adds a courier and their sign-in account. 201 + `Location`. Starts `OFFLINE`. 409 if the email or vehicle registration is taken; 400 `Unknown or inactive service area`. |
| `PATCH /{courierId}` | `vehicleType`, `vehicleRegistration`, `serviceAreaIds` (replaces the set; `[]` clears it). Send at least one. Every id must be an active zone (400), so drop a zone deactivated since. |
| `PATCH /{courierId}/availability` | `{"availability": "ONLINE" \| "ON_BREAK" \| "OFFLINE"}`. A deactivated courier can't go online. |

`POST /` takes `fullName`, `email`, `phone` (E.164), `password`, `vehicleType`, `vehicleRegistration`
and optional `serviceAreaIds`. The dispatcher sets the first password and passes it on; the courier can
change it with "Forgot password". The account is created active with the email already verified, so the
courier can sign in at `/courier/login` straight away.

Vehicle registrations are stored upper case with single spaces (`wp  cab 1234` → `WP CAB 1234`).

**Workload** (`CourierResponse.workload`): `assigned` (open, not started), `inProgress` (open, being
delivered), `completedToday` (`COMPLETED` since midnight, Sri Lanka time; `FAILED` doesn't count),
`open = assigned + inProgress`, `todaysTotal = open + completedToday`. The "12 / 15" on the screen is
`completedToday / todaysTotal`.

`serviceAreaIds` are delivery zone ids (config module, `docs/config.md`). Every courier also has
`serviceAreas: [{id, name}]` sorted by name, named with one `ZoneApi.zoneNames` call per list page or
courier; a zone deactivated since keeps its name.

## Database

| Migration | Change |
| --------- | ------ |
| `V6_001__couriers_availability_and_service_areas.sql` | Availability becomes `ONLINE` / `ON_BREAK` / `OFFLINE` (was `AVAILABLE` / `BUSY` / `OFFLINE`); new table `courier_service_areas (courier_id, zone_id)` |
| `V6_002__delivery_assignments_failed_status.sql` | `FAILED` added to `ck_delivery_assignments_status` |

## Not done yet

- Operations Overview data and the Failed Deliveries queue. **Retry needs a decision with Member 4:**
  `FAILED` is an end state, so a retried parcel needs a `FAILED -> CREATED` transition.
- Deactivating / reactivating a courier, and reacting when a courier's account is suspended (set them
  offline). Their open assignments are kept when they go offline or on break: the dispatcher reassigns them.
