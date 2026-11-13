# Config module

Owner: Member 6. Package `com.swiftdrop.config`; frontend folder `service-areas`.

The config module owns the **delivery zones** ("Service Areas" on the admin screens): which places
(district, city, postal code) each zone covers, and whether the zone is in use. Pricing finds zone
adjustments through it, and dispatch checks couriers' service areas against it.

> Pricing rules live in the `pricing` module, not here.

## For other modules: `ZoneApi`

Use `com.swiftdrop.config.api.ZoneApi`; never the config repositories or entities.

| Method | Use it for |
| ------ | ---------- |
| `findZoneId(district, city, postalCode)` | The active zone covering a place (`Optional`). `postalCode` is optional. |
| `missingOrInactive(zoneIds)` | Validating zone ids someone sent: the ids that are unknown or deactivated (empty = all fine). |
| `zoneNames(zoneIds)` | Names for display, active or inactive zones, in one query. Unknown ids are left out. |

### How a place is matched (`findZoneId`)

Only **active coverage rows of active zones** are considered. District, city and postal code are
trimmed (single spaces) and compared ignoring case; postal codes are stored upper case.

1. If a postal code is given: a row with the same district, city **and** postal code.
2. Otherwise, or if (1) found nothing: a row with the same district and city and **no** postal code
   (it covers the whole city).

Nothing else is tried: no district-only match, no "nearest" zone. A row with a postal code never
matches a place without one. Because of the overlap rule below there is at most one match; if older
data has more, the zone name decides (and a warning is logged).

### Who uses it

| Module | How |
| ------ | --- |
| pricing | `ConfigZoneResolver` (this module) is the pricing module's `ZoneResolver` bean: fee estimates resolve the pickup and drop-off zones and apply the zone adjustment of that route. It replaced the temporary `NoZoneResolver`. |
| dispatch | Couriers' `serviceAreaIds` must be active zones (`missingOrInactive`, else 400), the Couriers API shows their names (`zoneNames`), and a courier with service areas must cover a parcel's pickup zone to be assigned it (see `docs/dispatch.md`). |

## Rules

- **Names** are 2-100 characters (the `zone_name` column is `VARCHAR(100)`), trimmed, and unique
  **ignoring case** (409 with field `name`). The database index only checks exact case.
- **A zone lists each place once** (district, city, postal code; no postal code is a value of its own).
- **No place in two active zones:** an active coverage row must not match an active row of another
  active zone (same district, city and postal code, or both without one). Checked when coverage is
  added to an active zone, when a zone is created active, and again when a zone is **activated**;
  the 409 names the other zone. An inactive zone may list places other zones cover.
  A whole-city row and a postal-code row for the same city are *not* an overlap: matching prefers the
  postal code.
- **Zones are never deleted.** Addresses, pricing adjustments and courier service areas point at
  them without cascade, so a zone is **deactivated** instead (always allowed). Lookups ignore
  inactive zones; their names stay readable (`zoneNames`).
- Coverage rows are deleted for real: nothing references them.
- **Concurrent edits:** neither the case-insensitive name rule nor the overlap rule is a database
  constraint, so every zone/coverage write first takes a PostgreSQL transaction-scoped advisory lock
  (`ZoneWriteLock`, one constant key). Writes are rare and admin-only, so they simply queue.

## Service Areas API (`DISPATCHER_ADMIN` only)

All under `/api/v1/admin/service-areas`. Lists use Spring's paged JSON (`content`, `page`).

| Endpoint | Notes |
| -------- | ----- |
| `GET /?search&active&page&size` | Sorted by name, `size` 1-100 (default 20). `search` matches the zone name or any of its coverage cities, districts or postal codes. |
| `GET /{zoneId}` | The row fields plus `coverageAreas` (all rows, active or not, sorted by district, city, postal code). 404 if unknown. |
| `POST /` | `{name, description?, active? (default true), coverageAreas? [{district, city, postalCode?}]}` (at most 200). 201 + `Location` + the zone. |
| `PATCH /{zoneId}` | `{name?, description?}`; send at least one (400 otherwise). A blank description clears it. |
| `POST /{zoneId}/activate` | Re-checks the overlap rule (409 naming the other zone). Already active: nothing changes. |
| `POST /{zoneId}/deactivate` | Always allowed. |
| `POST /{zoneId}/coverage-areas` | `{district, city, postalCode?}`. 201 + the coverage row; `Location` is the zone (coverage rows have no URL of their own). 409 if the zone or another active zone already covers it. |
| `DELETE /{zoneId}/coverage-areas/{coverageId}` | 204; 404 if the row isn't in that zone. |

**Row** (list): `id, name, description, active, cities, districts, postalCodeRange, coverageCount,
createdAt`. The summary counts only **active** coverage rows: `cities` and `districts` are distinct
(ignoring case) and sorted; `postalCodeRange` is `"lowest-highest"` in string order (Sri Lankan
postal codes are five digits), the single code, or `null`; `coverageCount` is the number of active rows.
The coverage of a whole page is loaded in one query.

**Validation:** `name` 2-100; `description` at most 1000; `district` and `city` required, at most 100;
`postalCode` optional, `^[A-Za-z0-9 -]{3,15}$`.

## Not provided

- **"Type" and "Total Merchants"** from the Service Areas design: neither is stored anywhere, so the
  API doesn't return them (nothing is invented). Adding them needs a decision and a migration.
- Parcels don't store their zone yet: `BookingService` passes `zoneId = null` for both addresses, so
  dispatch resolves the pickup zone from the address each time (booking module, Member 3).
- Pricing accepts zone adjustments for any zone id; it doesn't check the zone is active through `ZoneApi`.

## Dev seed data

With the `dev` profile, `DevServiceAreaSeeder` adds eight demo zones when `delivery_zones` is empty:
Colombo 01 and Colombo 02-07 (city "Colombo", split by postal code 00100-00700, matching the dev
tracking demo's addresses), Colombo Suburbs, Gampaha, Kandy, Galle, Kurunegala (whole cities) and
Matara (inactive). It never runs in tests or production, and never touches existing zones.

## Database

No migration: the tables are in `V1__init.sql`.

| Table | Notes |
| ----- | ----- |
| `delivery_zones` | `zone_name VARCHAR(100)` unique (exact case), `is_active` |
| `zone_coverage_areas` | `district`, `city`, `postal_code` (nullable), `is_active`; cascade-deleted with its zone (which never happens) |
