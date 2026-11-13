# assistant (Member 5)

The AI delivery assistant chat widget for signed-in customers. The backend is the
`com.swiftdrop.assistant` module; see docs/assistant.md.

- `types.ts`: the API contract (`POST /api/v1/assistant/chat` and `/assistant/fee-estimate`).
- `src/api/assistant.ts`: the client (`assistantApi`); a 429 becomes `AssistantRateLimitError` with
  `retryAfterSeconds`. There is no mock: it always calls the backend, which answers in basic mode
  when no AI key is set.
- The widget is shown for `CUSTOMER` users from the dashboard layout (Phase 8).
