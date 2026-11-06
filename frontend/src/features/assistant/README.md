# assistant (Member 5)

The AI delivery assistant chat widget for customers and visitors. The backend is the
`com.swiftdrop.assistant` module; see docs/assistant.md.

- `types.ts`: the chat API contract (`POST /api/v1/public/assistant/chat`).
- The widget, its API client and a mock come in phase 4.

**Pending with Member 1:** approve this folder; mount the widget in `PublicLayout` and the customer
`DashboardLayout`; allow `/api/v1/public/assistant/**` without sign-in in `SecurityConfig`.
