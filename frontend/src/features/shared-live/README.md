# shared-live (Member 5)

Live parcel status updates over server-sent events (report section 3.7), for any screen.

- `types.ts`: the pushed message (`LiveStatusUpdate`) and the connection state.
- `useLiveUpdates` (coming in phase 2): opens the stream, reconnects with backoff, and calls you
  back with each newer update. On reconnect, reload the current state over REST: the pushed
  messages only keep the screen fresh, the API is the true record.

Streams:

| Stream                                                | Who              | Notes                                |
| ----------------------------------------------------- | ---------------- | ------------------------------------ |
| `GET /api/v1/public/tracking/{trackingNumber}/stream` | anyone           | One parcel                           |
| `GET /api/v1/admin/live/stream`                       | DISPATCHER_ADMIN | Every parcel; sends the bearer token |

Want live updates on your own screen (for example the signed-in Delivery Tracking page)? Use the
hook; ask Member 5 if it needs something more.
