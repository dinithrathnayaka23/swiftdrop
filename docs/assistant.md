# Assistant module

Owner: Member 5 (Asanka). Package `com.swiftdrop.assistant`; frontend folder `assistant`.
Owns no tables.

The customer-side AI delivery assistant. It answers questions about tracking, fees and the service
by calling tools:

| Tool | Uses |
| ---- | ---- |
| Track a parcel | The public tracking service (no personal data) |
| Estimate a fee | `FeeEstimateLookup` (see below) |
| Contact and service facts | `SystemSettingsService` |

The language model never receives personal data, and message text is never logged.

## Settings (`swiftdrop.assistant`, all optional)

| Setting | Default | Meaning |
| ------- | ------- | ------- |
| `provider` | `rules` | `rules` answers from simple rules with no key (demos and CI) |
| `api-key` | empty | Set only through `SWIFTDROP_ASSISTANT_API_KEY`; never commit it |
| `model`, `base-url` | empty | The provider's model and API URL |
| `timeout` | 30s | One model call |
| `max-message-chars` | 1000 | Longest user message |
| `max-history-messages` | 10 | Earlier messages sent with each question |
| `max-reply-tokens` | 600 | Longest reply |
| `rate-limit` | 20 / 1m | Questions per client IP or signed-in user |

## Waiting for other modules

| Interface (owned here) | Implemented by | Until then |
| ---------------------- | -------------- | ---------- |
| `assistant.service.FeeEstimateLookup` | An adapter here that calls Member 3's FeeApi | The assistant says fee estimates aren't available yet and links to booking |

Also pending with Member 1: allow `/api/v1/public/assistant/**` without sign-in, and mount the chat
widget in the public and customer layouts. Pending with Member 10: pass `SWIFTDROP_ASSISTANT_*`
to the backend container in docker-compose and list them in `.env.example`.
