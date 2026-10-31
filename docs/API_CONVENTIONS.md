# API conventions

## Paths

- Every endpoint lives under **`/api/v1`**. Build mappings from `ApiPaths` constants, never hard-code `/api/v1`.
- Resources are **plural, kebab-case nouns**: `/api/v1/parcels`, `/api/v1/pricing-rules`, `/api/v1/parcels/{parcelId}/status-history`.
- Actions that aren't plain CRUD use a sub-resource verb: `POST /api/v1/parcels/{id}/cancel`, `POST /api/v1/dispatch/assignments`.
- Public (no-auth) endpoints go under `/api/v1/public/...` (e.g. `/api/v1/public/tracking/{trackingNumber}`) or `/api/v1/auth/...`.
  Anything else requires a JWT. Ask the security owner before adding a new public route.

| Method | Use                     | Success code                    |
|--------|-------------------------|---------------------------------|
| GET    | read                    | 200                             |
| POST   | create / action         | 201 + `Location` header (create), 200/202 (action) |
| PUT    | full replace            | 200                             |
| PATCH  | partial update          | 200                             |
| DELETE | delete                  | 204                             |

## JSON and DTOs

- JSON fields are **camelCase**. Dates/times are ISO-8601 UTC strings (`Instant`), money is a decimal string or number with a separate `currency` field (LKR).
- Never expose JPA entities from controllers. Use DTOs in the module's `dto` package:
  - requests: `CreateParcelRequest`, `UpdateParcelStatusRequest`
  - responses: `ParcelResponse`, `ParcelSummaryResponse`
  - Prefer Java `record`s for DTOs; validate requests with Bean Validation (`@NotBlank`, `@Positive`, ...) and `@Valid`.
- IDs are UUIDs (as strings in JSON). Do not expose sequential IDs publicly.
- Lists are paginated: `?page=0&size=20&sort=createdAt,desc`, returning Spring's `Page` shape (or a wrapper we agree on).

## Errors

All errors use the standard body produced by `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-01-01T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/parcels",
  "fieldErrors": [{ "field": "weightKg", "message": "must be greater than 0" }]
}
```

- Throw `NotFoundException`, `BadRequestException`, `ConflictException` or `ForbiddenException` from services.
  Don't build error responses by hand in controllers.
- Messages must be safe to show to users: no stack traces, SQL, class names or personal data.
- Add new machine-readable codes to `ErrorCode` rather than inventing strings.

## Security

- Use `@PreAuthorize("hasRole('COURIER')")` (etc.) on controller methods. Roles: `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER_ADMIN`.
- Get the caller with `@AuthenticationPrincipal AuthenticatedUser user`. Always check ownership
  (e.g. a customer can only read their own parcels); role checks alone are not enough.
- Merchant server-to-server calls will use an `X-API-Key` header (merchant module).

## Database naming

The schema follows the team's ER diagram; see [DATA_MODEL.md](DATA_MODEL.md).

- Tables: **plural snake_case** (`parcels`, `pricing_rules`); columns: snake_case (`created_at`).
- Primary key `<entity>_id UUID DEFAULT gen_random_uuid()` (`user_id`, `parcel_id`); foreign keys use the
  referenced key's name, optionally with a role prefix (`customer_user_id`, `assigned_by_user_id`).
- Timestamps: `TIMESTAMPTZ` (`created_at`, plus `updated_at` on tables whose rows change).
- Status/type columns are `VARCHAR` with a `CHECK` constraint listing the allowed values, mapped to a Java enum.
- Money: `NUMERIC(10,2)` plus a `CHAR(3)` currency column (default `LKR`).
- Constraint names: index `ix_<table>_<cols>`, unique `ux_<table>_<cols>`, foreign key `fk_<table>_<ref>`,
  check `ck_<table>_<rule>`.

## Messaging

- Exchange `swiftdrop.parcel.events` (topic). Routing keys `<aggregate>.<event>`: `parcel.status.changed`.
- Events are Java records in `common.messaging.event`; contain IDs, not personal data.
- Consumers must be idempotent (messages may be delivered more than once). Failed messages go to the DLQ after 3 attempts.
