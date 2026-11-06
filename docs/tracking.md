# Tracking module

Owner: Member 5 (Asanka). Package `com.swiftdrop.tracking`; frontend folders `landing`,
`public-tracking` and `admin-live-tracking`. Owns no tables: parcels are read only through
`ParcelService` and `ParcelStatusService` (see parcel.md).

## Public tracking

`GET /api/v1/public/tracking/{trackingNumber}`: no sign-in. Body: `PublicTrackingResponse`, the same
shape as the frontend's `TrackingResult`. 404 when there is no parcel with that number.

**Privacy:** only the tracking number, status codes, timestamps, from/to city and weight. Never
names, phone numbers, street addresses, notes, failure reasons or proof.

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
