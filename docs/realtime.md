# Realtime module

Owner: Member 5 (Asanka). Package `com.swiftdrop.realtime`; frontend folder `shared-live`.
Owns no tables.

Pushes parcel status changes to open browsers over server-sent events (report section 3.7):

1. The browser loads the current state over REST (`GET /api/v1/public/tracking/{trackingNumber}`).
2. It opens a stream.
3. When a status change is committed, the parcel module publishes `ParcelStatusChangedEvent`; this
   module receives a copy and pushes a `LiveStatusUpdate` to the matching streams.
4. If the connection drops, the browser reconnects and reloads the current state.

Pushed updates only keep screens fresh: the database is the real record.

## How it works

```
ParcelStatusService.changeStatus ──(after commit)──▶ [swiftdrop.parcel.events]
        ──parcel.status.#──▶ swiftdrop.realtime.<random>   (RealtimeQueueConfig)
        ──▶ ParcelStatusEventListener ── skips duplicates / older versions (RecentStatusEvents)
        ──▶ LiveStatusUpdate (app event) ──▶ LiveStreamHub ──▶ open streams of that parcel + admin streams
```

| Class | Does |
| ----- | ---- |
| `config.RealtimeQueueConfig` | Its own temporary queue (non-durable, exclusive, auto-delete), bound with `parcel.status.#`. Every backend instance gets every event; nothing is replayed after a restart. The shared `RabbitConfig` is not changed. |
| `event.ParcelStatusEventListener` | Reads the queue, drops incomplete events, duplicates (same `eventId`) and older events (`statusVersion` not newer than the last one seen for the parcel), then announces a `LiveStatusUpdate`. |
| `event.RecentStatusEvents` | That memory: the last 2,000 event ids and the latest version of the last 10,000 parcels, in memory. |
| `service.LiveStreamHub` | The open streams of this instance: per tracking number, plus admin streams that get every parcel. Per-IP limit, heartbeats, timeouts, cleanup. |
| `controller.PublicLiveTrackingController` | The public stream endpoint. |

## Public stream

`GET /api/v1/public/tracking/{trackingNumber}/stream`: no sign-in. Case and surrounding spaces in
the number are ignored. Response `text/event-stream` with `Cache-Control: no-cache` and
`X-Accel-Buffering: no` (nginx passes events through without buffering, so `nginx.conf` needs no
change).

```
:connected

event:status
data:{"trackingNumber":"SD4QX9NPAG99KG","previousStatus":"CREATED","status":"PICKED_UP","statusVersion":2,"changedAt":"2026-11-07T04:30:00Z"}

:ping
```

- `:connected` is sent first, so the response starts at once.
- `event: status` carries a `LiveStatusUpdate`: tracking number, previous and new status codes,
  `statusVersion` (1 for CREATED, then +1 per change) and `changedAt`. No ids, names, addresses,
  notes, failure reasons or proof. The frontend type is `features/shared-live/types.ts`.
- **Apply an update only if its `statusVersion` is newer than what the page shows.** After a
  reconnect, reload the current state over REST first.
- `:ping` comments every `heartbeat-interval` (25 s) keep idle streams open through nginx (60 s read
  timeout) and find browsers that left.
- After `stream-timeout` (30 min) the server ends the stream normally; the browser reconnects. The
  server also ends every stream when it shuts down.

| Status | When | Body |
| ------ | ---- | ---- |
| 200 | Stream open | The events above |
| 404 `NOT_FOUND` | No parcel with that number | Same message as the look-up |
| 429 `TOO_MANY_REQUESTS` | More than `stream-rate-limit` new streams per IP (30 / min), or `max-streams-per-client` already open (5) | `Retry-After` header (seconds) |
| 503 `MAINTENANCE_MODE` | Maintenance mode is on | Standard error body |

Errors are always the standard JSON error body, even when the request only accepts
`text/event-stream` (the controller sets the content type itself).

The admin stream `GET /api/v1/admin/live/stream` (DISPATCHER_ADMIN, every parcel) uses the same hub;
its endpoint comes with the admin Deliveries Tracking board.

## Settings

In `swiftdrop.tracking` (see `docs/tracking.md`): `stream-rate-limit`, `max-streams-per-client`,
`stream-timeout`, `heartbeat-interval`. Keep `heartbeat-interval` below nginx's
`proxy_read_timeout` (60 s).

Streams live in one instance's memory: with several backend instances, each holds its own streams
and gets every event from its own queue.

## Try it

With the backend on the `dev` profile, create a parcel with the dev demo helper (see
`docs/tracking.md`), then in one PowerShell window:

```powershell
curl.exe -N "http://localhost:8080/api/v1/public/tracking/$n/stream"
```

and advance the parcel from another (`POST /api/v1/admin/dev/tracking/$n/advance`). Each change
appears as an `event:status` line straight away.

## Tests

- `ParcelStatusEventListenerTest`: mapping to public fields, duplicates, older events.
- `LiveStreamHubTest`: who gets which update, the per-IP limit, heartbeats, timeouts and cleanup.
- `PublicLiveTrackingIT`: a committed status change reaches an open stream over HTTP; headers,
  heartbeat, 404 and both 429 limits.
