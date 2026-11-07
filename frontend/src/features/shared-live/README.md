# shared-live (Member 5)

Live parcel status updates over server-sent events (report section 3.7), for any screen.

- `types.ts`: the pushed message (`LiveStatusUpdate`) and the connection state.
- `useLiveUpdates`: opens the stream, reconnects with backoff (1 s up to 30 s, then "offline"),
  and calls you back with each newer update. On reconnect (`onReconnect`), reload the current state
  over REST: the pushed messages only keep the screen fresh, the API is the true record.
- `LiveIndicator`: the small "Live / Connecting… / Live updates paused" badge for that state.
- `eventStream.ts`: reads the stream with `fetch()`, so it can send a bearer token and read error
  codes. Used by the hook; you shouldn't need it directly.

```tsx
const live = useLiveUpdates({
  path: `/public/tracking/${encodeURIComponent(trackingNumber)}/stream`,
  knownVersions: { [trackingNumber]: data.history.length },
  onUpdate: (update) => showNewStatus(update),
  onReconnect: reload,
});
// live: 'connecting' | 'live' | 'offline'
```

Streams:

| Stream                                                | Who              | Notes                                |
| ----------------------------------------------------- | ---------------- | ------------------------------------ |
| `GET /api/v1/public/tracking/{trackingNumber}/stream` | anyone           | One parcel                           |
| `GET /api/v1/admin/live/stream`                       | DISPATCHER_ADMIN | Every parcel; sends the bearer token |

Want live updates on your own screen (for example the signed-in Delivery Tracking page)? Use the
hook; ask Member 5 if it needs something more.
