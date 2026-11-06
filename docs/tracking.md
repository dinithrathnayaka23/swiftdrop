# Tracking module

Owner: Member 5 (Asanka). Package `com.swiftdrop.tracking`; frontend folders `landing`,
`public-tracking` and `admin-live-tracking`. Owns no tables: parcels are read only through
`ParcelService` and `ParcelStatusService` (see parcel.md).

## Public tracking

`GET /api/v1/public/tracking/{trackingNumber}`: no sign-in. Body: `PublicTrackingResponse`, the same
shape as the frontend's `TrackingResult`. Case and surrounding spaces in the number are ignored.

```json
{
  "trackingNumber": "SD12345678",
  "currentStatus": "IN_TRANSIT",
  "lastUpdatedAt": "2026-11-02T03:10:00Z",
  "bookedAt": "2026-11-01T04:00:00Z",
  "fromCity": "Colombo",
  "toCity": "Kandy",
  "weightKg": 2.5,
  "history": [
    { "statusCode": "CREATED", "changedAt": "2026-11-01T04:00:00Z" },
    { "statusCode": "PICKED_UP", "changedAt": "2026-11-01T06:30:00Z" },
    { "statusCode": "IN_TRANSIT", "changedAt": "2026-11-02T03:10:00Z" }
  ]
}
```

- `fromCity` / `toCity` are the pickup and drop-off cities. `history` is oldest first. `lastUpdatedAt`
  is the time of the latest status change.
- **404 `NOT_FOUND`** with the same message for every unknown, blank or malformed number.
- **429 `TOO_MANY_REQUESTS`** with a `Retry-After` header (seconds) after `lookup-rate-limit` look-ups
  (default 60 per minute) from one client IP. Found and not-found look-ups both count. The IP is
  `request.getRemoteAddr()`, which is the visitor's IP behind nginx (`forward-headers-strategy: native`).
- **503 `MAINTENANCE_MODE`** while maintenance mode is on.

**Privacy:** only the tracking number, status codes, timestamps, from/to city and weight. Never
names, phone numbers, street addresses, postal codes, notes, failure reasons, proof, or any ids.
`PublicTrackingMapperTest` checks the serialized JSON for this.

Code: `PublicTrackingController` → `PublicTrackingService` (`ParcelService.findByTrackingNumber`,
`ParcelStatusService.getHistory`) → `PublicTrackingMapper`.

## Dev demo helper (dev profile only)

Dispatch and the courier app don't change statuses yet, so for demos the `dev` profile adds two
`DISPATCHER_ADMIN` endpoints. They don't exist in any other profile.

| Endpoint | Does |
| -------- | ---- |
| `POST /api/v1/admin/dev/tracking/sample-parcel` | Creates a sample CREATED parcel (Colombo → Kandy, 1.8 kg) through `ParcelService.createParcel`, booked as the signed-in admin. 201 with `{ "trackingNumber": "SD…" }` and a `Location` header pointing at its public tracking. |
| `POST /api/v1/admin/dev/tracking/{trackingNumber}/advance` | Moves the parcel one step along the normal flow (CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED) through `ParcelStatusService.changeStatus`. `?to=FAILED` fails it instead (not possible from CREATED). Returns the new public tracking. 409 for DELIVERED or FAILED parcels, 400 for any other `to`, 404 for an unknown number. |

Each change publishes the usual `ParcelStatusChangedEvent`, so live updates can be tested too.

```powershell
$h = @{ Authorization = "Bearer $token" }   # an admin access token
$n = (Invoke-RestMethod -Method Post http://localhost:8080/api/v1/admin/dev/tracking/sample-parcel -Headers $h).trackingNumber
Invoke-RestMethod -Method Post "http://localhost:8080/api/v1/admin/dev/tracking/$n/advance" -Headers $h
Invoke-RestMethod -Method Post "http://localhost:8080/api/v1/admin/dev/tracking/$n/advance?to=FAILED" -Headers $h
Invoke-RestMethod "http://localhost:8080/api/v1/public/tracking/$n"
```

## Settings (`swiftdrop.tracking`, all optional)

| Setting | Default | Meaning |
| ------- | ------- | ------- |
| `lookup-rate-limit` | 60 / 1m | Public look-ups per client IP |
| `stream-rate-limit` | 30 / 1m | New live streams per client IP |
| `max-streams-per-client` | 5 | Open streams per client IP |
| `stream-timeout` | 30m | A stream is closed after this; the browser reconnects |
| `heartbeat-interval` | 25s | Keeps idle streams open through nginx (60s read timeout) |

## Waiting for other modules

| Interface (owned here) | Implemented by | Until then |
| ---------------------- | -------------- | ---------- |
| `tracking.service.CourierLookup` | Member 6 (dispatch), or an adapter here that calls CourierApi | Courier column on the admin board shows "—" |
