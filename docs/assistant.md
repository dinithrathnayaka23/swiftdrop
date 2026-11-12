# Assistant module

Owner: Member 5 (Asanka). Package `com.swiftdrop.assistant`; frontend folder `assistant`. Owns no tables.

The AI delivery assistant for **signed-in customers** (role `CUSTOMER`). It answers questions about tracking, the
customer's own recent parcels, delivery fees, parcel types and contact details by calling SwiftDrop's own services
as tools. It never guesses: facts come only from tool results, and the reply carries **cards** built by the server
from those results. There is no RAG, multi-agent setup, fine-tuning or MCP.

The model is Google **Gemini** (free tier) through its OpenAI-compatible Chat Completions endpoint, called with
Spring `RestClient` (no SDK). Switching to OpenAI is a settings change. Without a key, or when the model fails, an
offline rule-based client answers instead ("Basic mode").

## Endpoints

Both need a customer's bearer token (`@PreAuthorize("hasRole('CUSTOMER')")`): visitors get 401, other roles 403.
Both share one rate limit per customer.

### `POST /api/v1/assistant/chat`

The browser keeps the conversation and sends it each time, oldest first, ending with the new question. Only `user`
and `assistant` text is accepted; the server keeps the latest 10.

```json
{ "messages": [ { "role": "user", "content": "Where is SD7KQ2MX9TRB4H?" } ] }
```

```json
{
  "reply": "SD7KQ2MX9TRB4H is out for delivery in Kandy.",
  "cards": [ { "type": "TRACKING", "tracking": { "trackingNumber": "SD7KQ2MX9TRB4H", "currentStatus": "OUT_FOR_DELIVERY", "...": "PublicTrackingResponse" } } ],
  "suggestions": ["Where are my parcels?", "How much to send 2 kg?"],
  "mode": "AI"
}
```

- `reply` is plain text; the widget renders no HTML or markdown.
- `cards[].type` is `TRACKING` (`tracking`: the public tracking response), `FEE` (`fee`: `totalFee`, `currency`,
  `lines[]`) or `PARCELS` (`parcels[]`: `trackingNumber`, `status`, `statusLabel`, `fromCity`, `toCity`,
  `lastUpdatedAt`).
- `mode` is `AI`, or `RULES` when Basic mode answered (no key, or the model failed). The widget shows "Basic mode".

### `POST /api/v1/assistant/fee-estimate`

The widget's fee form. Runs `estimate_fee` directly: no model, no tokens.

```json
{ "pickupDistrict": "Colombo", "pickupCity": "Colombo", "dropoffDistrict": "Kandy", "dropoffCity": "Kandy",
  "weightKg": 2, "lengthCm": null, "widthCm": null, "heightCm": null }
```

```json
{ "cards": [ { "type": "FEE", "fee": { "totalFee": 750.00, "currency": "LKR", "lines": [ { "label": "Base fee", "amount": 500.00 } ] } } ],
  "message": "The delivery fee is LKR 750.00." }
```

With no active pricing rule: `cards` is empty and `message` says prices aren't available, with the contact details.

### Errors

| Status | When |
| ------ | ---- |
| 400 `VALIDATION_FAILED` | Empty conversation, more than 50 messages, a role other than `user`/`assistant`, a blank message, the last message not from the user, or invalid fee form fields |
| 400 `BAD_REQUEST` | A user message over `max-message-chars`, or a fee form value over the limits in system settings |
| 401 / 403 | Not signed in / not a customer |
| 429 `TOO_MANY_REQUESTS` | Over `rate-limit` for this customer; see `Retry-After` |
| 503 `MAINTENANCE_MODE` | Maintenance mode is on |

## How a question is answered

```
AssistantController (role, rate limit, validation)
  -> AssistantService: length check, keep the last 10 messages, hide emails and phone numbers
  -> FallbackLlmClient -> OpenAiCompatibleLlmClient (Gemini)   or   RuleBasedLlmClient
  -> up to 3 rounds: model asks for tools -> ToolRegistry (allowlist, argument checks) -> tool -> result back
  -> final text + cards from the tool results + suggestions
```

The wire format is standard Chat Completions: `POST <base>/chat/completions` with `model`, `messages` (system prompt,
then the history), `tools`, `tool_choice: "auto"` and `max_tokens`. Tool requests come back as
`choices[0].message.tool_calls`; each result goes back as a `role: "tool"` message. No provider-specific fields
(`store`, `strict`) are sent. Tools without arguments are sent without `parameters`, because Gemini rejects an object
schema with no properties. The system prompt is in `config/AssistantPrompts`.

## Tools

| Tool | Arguments | Model gets | Card |
| ---- | --------- | ---------- | ---- |
| `track_parcel` | `trackingNumber` (`^SD[2-9A-HJKMNP-Z]{12}$` after trimming and upper-casing) | Status, a server-written label and description, last updated, booked at, from/to city, weight, history. Or `{found:false}` / `{error:"RATE_LIMITED"}` | `TRACKING` |
| `estimate_fee` | `pickupDistrict`, `pickupCity`, `dropoffDistrict`, `dropoffCity` (≤ 60 chars), `weightKg` (> 0, ≤ max in settings); optional `lengthCm`, `widthCm`, `heightCm` (≤ max size) | `totalFee`, `currency`, `lines[]`. Or `{error:"PRICING_UNAVAILABLE"}` with the contact details | `FEE` |
| `get_service_info` | none | Company name, contact email and phone, parcel types with descriptions, max weight and size, how tracking works | none |
| `list_my_recent_parcels` | none: the customer comes from the sign-in token, never from the model | Up to 5 latest parcels: tracking number and status only | `PARCELS` (adds cities and dates) |

Every result also has a plain `message` sentence written by the server; Basic mode shows it as the reply. No admin
or write tools exist.

## Guardrails

- **Customers only**, and the customer for `list_my_recent_parcels` always comes from the token.
- **Limits:** `max-message-chars` (1000) per user message, the latest `max-history-messages` (10) messages, at most
  50 in a request, `max-reply-tokens` (600), at most 3 tool rounds (then "please rephrase") and 4 calls per round.
- **Rate limits:** `rate-limit` per customer for both endpoints (key `assistant:customer:<userId>`), and
  `track_parcel` counts against the public tracking limit for the client IP (`tracking:lookup:<ip>`), so the chat
  can't be used to guess tracking numbers.
- **Tool calls:** allowlist only. Unknown tools, arguments that aren't a JSON object, unknown argument names and
  invalid values become error results for the model; they never reach a service.
- **Personal data:** `MessageSanitizer` replaces emails and phone numbers (Sri Lankan and `+country` formats) before
  anything goes to the model. Tool results hold only public-safe data. On Gemini's free tier Google may use prompts to
  improve its products and people may review them, so the widget says "Don't share personal details in the chat."
- **Logs:** tool name, time taken and outcome; per question the time, mode, token count and number of cards; the
  reason when falling back (e.g. `HTTP 429`). Never message text, never the key.

## Failure handling

| Failure | What happens |
| ------- | ------------ |
| No key or model configured | Basic mode for every question (`mode: RULES`) |
| 429 (free quota used up), timeout, 5xx, unreadable or empty reply | That round is answered by Basic mode; the model is not called again for 60 seconds. No retries |
| Bad tool arguments or an unknown tool | An error result goes back to the model; after 3 rounds, a safe "please rephrase" reply |
| No active pricing rule | "Prices aren't available right now", plus the contact details |
| Unknown tracking number | "I couldn't find that number." No hints |
| Tracking limit hit inside the chat | "Too many look-ups, please wait a minute." |

Basic mode understands simple English: a tracking number, "my parcels", contact, weight and "what can I send"
questions, and points price questions to the fee form.

## Settings (`swiftdrop.assistant`, all optional)

| Setting | Environment variable | Default | Meaning |
| ------- | -------------------- | ------- | ------- |
| `provider` | `SWIFTDROP_ASSISTANT_PROVIDER` | `rules` | `gemini`, `openai` or `rules` (Basic mode, no key) |
| `api-key` | `SWIFTDROP_ASSISTANT_API_KEY` | empty | From Google AI Studio. Never commit it; `toString()` hides it |
| `model` | `SWIFTDROP_ASSISTANT_MODEL` | empty | A current Flash-Lite model name from AI Studio (biggest free quota) |
| `base-url` | `SWIFTDROP_ASSISTANT_BASE_URL` | per provider | `gemini`: `https://generativelanguage.googleapis.com/v1beta/openai/`; `openai`: `https://api.openai.com/v1` |
| `timeout` | `SWIFTDROP_ASSISTANT_TIMEOUT` | `30s` | Connect and read timeout for one model call |
| `max-message-chars` | `SWIFTDROP_ASSISTANT_MAX_MESSAGE_CHARS` | 1000 | Longest user message |
| `max-history-messages` | `SWIFTDROP_ASSISTANT_MAX_HISTORY_MESSAGES` | 10 | Messages sent to the model |
| `max-reply-tokens` | `SWIFTDROP_ASSISTANT_MAX_REPLY_TOKENS` | 600 | `max_tokens` for the model |
| `rate-limit` | `SWIFTDROP_ASSISTANT_RATE_LIMIT_REQUESTS`, `_WINDOW` | 20 / 1m | Questions per customer |

The model is used only when the provider isn't `rules` and a key, a model and a base URL are all set. The startup
log says which mode is active ("Assistant mode: ...").

### How the values reach the backend

- **Docker Compose:** put the four `SWIFTDROP_ASSISTANT_*` values in the repo-root `.env` (see `.env.example`).
  `docker-compose.yml` passes them to the backend container as environment variables.
- **`.\mvnw.cmd` with the `dev` profile:** put the same values in the repo-root `.env` and run

  ```powershell
  cd backend
  .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
  ```

  `application-dev.yml` imports `.env` as a plain properties file and maps the four values with placeholders
  (`api-key: ${SWIFTDROP_ASSISTANT_API_KEY:}` and so on). The placeholders are needed: Spring maps the upper-case
  `SWIFTDROP_ASSISTANT_API_KEY` form to `swiftdrop.assistant.api-key` by itself only for real environment
  variables, not for values read from a file. Real environment variables still win over `.env`.
- **Other profiles without Docker** (no placeholders): set them as real environment variables, e.g.
  `$env:SWIFTDROP_ASSISTANT_API_KEY = "..."`. Without them the backend starts in Basic mode.

## Testing and evaluation

- Unit tests: each tool, `ToolRegistry`, `MessageSanitizer`, `RuleBasedLlmClient`, `FallbackLlmClient`,
  `AssistantService` and the whole tool loop with a scripted fake model. `OpenAiCompatibleLlmClientTest` uses
  `MockRestServiceServer` with recorded Chat Completions replies (tool calls, final answer, 429, 5xx).
- `AssistantIT`: both endpoints over HTTP in Basic mode (200, 400, 401/403, 429, no personal data, own parcels only).
- **CI never calls a real model.**
- [assistant-eval.md](assistant-eval.md): prompts to run by hand with the real key, to record whether the right tool
  was used and the answer stayed grounded.

## Later (v2)

Streaming replies over SSE, RAG over policy documents once they exist, and an MCP server for the same tools.
