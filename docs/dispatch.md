# Dispatch module

Owner: Member 6. Package `com.swiftdrop.dispatch`; frontend folders `dispatch` and `couriers`.

The dispatch module owns couriers (profile, vehicle, service areas, availability) and the
assignment of parcels to couriers. A courier is a `COURIER` account (user module) plus a profile row
(`couriers`) owned by this module.

> **Status:** courier management is built. Assigning parcels, the Operations Overview data and the
> failed-delivery queue are not (see "Not done yet").

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

An assignment is **open** while `delivery_assignments.ended_at IS NULL`. Whoever completes, reassigns
or cancels an assignment must set `ended_at` (and the status): the "one open assignment per parcel"
index, the Assigned tab and the workload counts all rely on it.

## Couriers API (`DISPATCHER_ADMIN` only)

All under `/api/v1/admin/couriers`. Lists use Spring's paged JSON (`content`, `page`), newest first,
`size` 1-100 (default 20).

| Endpoint | Notes |
| -------- | ----- |
| `GET /?search&availability&serviceAreaId&vehicleType&active&page&size` | `search` matches name, email, phone or vehicle registration. Every row has today's `workload`. |
| `GET /summary` | `total`, `online`, `onBreak`, `offline` (active couriers only) |
| `GET /{courierId}` | One courier |
| `POST /` | Adds a courier and their sign-in account. 201 + `Location`. Starts `OFFLINE`. 409 if the email or vehicle registration is taken, or a service area doesn't exist. |
| `PATCH /{courierId}` | `vehicleType`, `vehicleRegistration`, `serviceAreaIds` (replaces the set; `[]` clears it). Send at least one. |
| `PATCH /{courierId}/availability` | `{"availability": "ONLINE" \| "ON_BREAK" \| "OFFLINE"}`. A deactivated courier can't go online. |

`POST /` takes `fullName`, `email`, `phone` (E.164), `password`, `vehicleType`, `vehicleRegistration`
and optional `serviceAreaIds`. The dispatcher sets the first password and passes it on; the courier can
change it with "Forgot password". The account is created active with the email already verified, so the
courier can sign in at `/courier/login` straight away.

Vehicle registrations are stored upper case with single spaces (`wp  cab 1234` → `WP CAB 1234`).

**Workload** (`CourierResponse.workload`): `assigned` (open, not started), `inProgress` (open, being
delivered), `completedToday` (since midnight, Sri Lanka time), `open = assigned + inProgress`,
`todaysTotal = open + completedToday`. The "12 / 15" on the screen is `completedToday / todaysTotal`.

`serviceAreaIds` are delivery zone ids. Zone names come from the Service Areas API once it exists.

## Database

| Migration | Change |
| --------- | ------ |
| `V6_001__couriers_availability_and_service_areas.sql` | Availability becomes `ONLINE` / `ON_BREAK` / `OFFLINE` (was `AVAILABLE` / `BUSY` / `OFFLINE`); new table `courier_service_areas (courier_id, zone_id)` |

## Not done yet

- Assigning and reassigning parcels (single and bulk), and keeping assignment status in step with parcel
  status changes (listening to `ParcelStatusChangedEvent`).
- Operations Overview data and the Failed Deliveries queue. **Retry needs a decision with Member 4:**
  `FAILED` is an end state, so a retried parcel needs a `FAILED -> CREATED` transition.
- Deactivating / reactivating a courier, and reacting when a courier's account is suspended (set them offline).
- Zone names and the zone check on assignment (waiting for the zones backend).
