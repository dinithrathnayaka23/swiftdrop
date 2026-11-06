# Realtime module

Owner: Member 5 (Asanka). Package `com.swiftdrop.realtime`; frontend folder `shared-live`.
Owns no tables.

Pushes parcel status changes to open browsers over server-sent events (report section 3.7):

1. The browser loads the current state over REST.
2. It opens a stream.
3. When a status change is committed, the parcel module publishes `ParcelStatusChangedEvent`; this
   module receives a copy and pushes a `LiveStatusUpdate` to the matching streams.
4. If the connection drops, the browser reconnects and reloads the current state.

## Queue

`RealtimeQueueConfig` declares its own queue, bound to `swiftdrop.parcel.events` with
`parcel.status.#`. It is temporary (non-durable, exclusive, auto-delete, named
`swiftdrop.realtime.<random>`): live pushes don't need to survive a restart. The shared
`RabbitConfig` is not changed.

## Streams

| Endpoint | Who |
| -------- | --- |
| `GET /api/v1/public/tracking/{trackingNumber}/stream` | anyone |
| `GET /api/v1/admin/live/stream` | DISPATCHER_ADMIN |

Each sends `event: status` with a `LiveStatusUpdate` (`trackingNumber`, `previousStatus`, `status`,
`statusVersion`, `changedAt`) and a comment heartbeat every 25 seconds. Responses set
`X-Accel-Buffering: no`, so nginx passes events through without buffering.
