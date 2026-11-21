# Merchant portal and API keys

Owner: Member 8 (scope slice 8, *Merchant Portal & API Keys*). The code currently lives in the
shared `com.swiftdrop.merchant` package (with Member 2's merchant registration and Member 9's order
API and webhooks). Frontend folder: `merchant-portal`. Table: `merchant_api_credentials`.

## Who can use it

- Only users with the `MERCHANT` role, signed in through the public sign-in page.
- Only for an **APPROVED** merchant. `MerchantAccessService.requireApprovedMerchant` resolves the
  signed-in user's merchant and scopes every query to its id, so a merchant only ever sees its own
  data.
- Blocked statuses answer **403**. The order API (API key) blocks the same statuses through the
  same check.

| Merchant status | Code | Message |
| --- | --- | --- |
| `PENDING` | `ACCOUNT_PENDING_APPROVAL` | "Your merchant account is awaiting approval…" |
| `REJECTED`, `SUSPENDED` | `ACCOUNT_DISABLED` | "This merchant account is not active…" (one message, so the reason isn't revealed) |
| A `MERCHANT` user with no merchant | `FORBIDDEN` | "No merchant account is linked to this user" |

## Screens

| Path | Screen | Data |
| --- | --- | --- |
| `/merchant` | Dashboard | Profile, order counts per status, 5 most recent orders, integration status (API keys and webhook) |
| `/merchant/orders` | Orders | Paged list, newest first. Search box, status chips and a creation date range. The filters and the page are kept in the address (`?search=&status=&from=&to=&page=`), so a filtered list can be bookmarked or shared. A changed filter goes back to page 1. |
| `/merchant/orders/:trackingNumber` | Order Details | Addresses, parcel, fee, progress timeline, failure reason |
| `/merchant/profile` | Business Profile | View; edit contact email, phone and address |
| `/merchant/integration` | API Integration | API keys (create, revoke, rotate) and setup steps |

`/merchant/delivery-updates` (webhook settings and log) belongs to Member 9.

## Portal endpoints

All endpoints need `Authorization: Bearer <JWT>` with the `MERCHANT` role and an approved merchant.

| Method | Path | Does |
| --- | --- | --- |
| GET | `/api/v1/merchant/profile` | The merchant's business profile |
| PUT | `/api/v1/merchant/profile` | Updates `contactEmail`, `contactPhone` (E.164, e.g. `+94771234567`) and `businessAddress` (10–500 characters). Business name and registration number can't be changed here. |
| GET | `/api/v1/merchant/orders/counts` | Orders per parcel status |
| GET | `/api/v1/merchant/orders?page=0&size=10` | Paged orders, newest first. `page` ≥ 0, `size` 1–100, otherwise 400. Optional filters, combined with AND (see below). |
| GET | `/api/v1/merchant/orders/{trackingNumber}` | One order. 404 if it doesn't exist or belongs to another merchant. |
| GET | `/api/v1/merchant/api-keys` | The merchant's keys, newest first (never the secret) |
| POST | `/api/v1/merchant/api-keys` | Creates a key. **The only response that contains the raw key.** 409 at the active-key limit. |
| POST | `/api/v1/merchant/api-keys/{apiCredentialId}/revoke` | Stops the key working at once. Revoking twice changes nothing. |
| POST | `/api/v1/merchant/api-keys/{apiCredentialId}/rotate` | Revokes an active key and returns its replacement in one step (works at the limit). 409 for a revoked or expired key. |

A key that belongs to another merchant answers 404, not 403, so key ids can't be probed.

### Orders filters

| Parameter | Matches | Bad value (400 `BAD_REQUEST`) |
| --- | --- | --- |
| `search` | Part of the tracking number, order reference, recipient name or delivery city, ignoring case. `%` and `_` match only themselves. Surrounding spaces are ignored. | More than 100 characters: "search must be at most 100 characters" |
| `status` | Current status, e.g. `IN_TRANSIT` (case ignored) | "status must be one of CREATED, PICKED_UP, …" |
| `from`, `to` | Creation day, `yyyy-MM-dd`, Sri Lanka time. Both days are included; either can be left out. | "from must be a date in the format yyyy-MM-dd"; "from must be on or before to" |

Blank parameters are ignored. Filtering is done in the database through
`ParcelService.listByMerchant(merchantId, MerchantParcelFilter, Pageable)`, always scoped to the
signed-in merchant.

## API keys

- **Format:** `sd_<keyId>_<secret>`. The `keyId` is public: it's shown in the portal and is the
  OAuth `client_id`. The secret is 256 random bits.
- **Storage:** only an HMAC-SHA256 of the key is stored, keyed with the server pepper
  (`API_KEY_PEPPER`, at least 32 characters). A copy of the database alone isn't enough to check
  guessed keys. Changing the pepper invalidates every issued key.
- **Shown once:** the raw key is returned only by create and rotate. Logs contain key ids only.
- **Limit:** 2 active keys per merchant by default (`swiftdrop.merchant.api-keys.max-active-keys`),
  so a merchant can switch to a new key before revoking the old one.
- **Statuses:** `ACTIVE`, `REVOKED` (revoked at), `EXPIRED` (past `expires_at`).
- **Last used:** `last_used_at` is updated on use, at most once a minute.

## `ApiKeyApi` (for other modules)

Other modules check keys only through `com.swiftdrop.merchant.api.ApiKeyApi`, never through
`MerchantApiKeyService`, the entity or the repository. It's implemented by
`MerchantApiKeyService`.

```java
Optional<ValidatedApiKey> validate(String apiKey);          // merchantId, apiCredentialId, keyId
Optional<MerchantSummary> getMerchantForKey(String apiKey); // the approved merchant behind the key
```

| Key | Result |
| --- | --- |
| Null, blank, malformed, unknown, wrong, revoked or expired | Empty (the caller answers 401 without saying which). No database look-up for null, blank or malformed keys. |
| Valid, merchant not approved | Throws `MerchantNotApprovedException` (403) |
| Valid, merchant approved | The key or merchant; `last_used_at` is recorded |

Used by:

- `ApiKeyAuthenticationFilter`: the `X-API-Key` header on `/api/v1/orders/**` (Member 9's order
  API). It sets an `AuthenticatedMerchant` principal with the `ROLE_MERCHANT_API` authority.
- `OAuthTokenController`: `POST /api/v1/oauth/token` with `grant_type=client_credentials`,
  `client_id=<keyId>` and `client_secret=<raw key>`. Returns a 15-minute Bearer token for the
  order API.
- `SecurityConfig`: registers the filter only when an `ApiKeyApi` bean exists.

## Try it locally

1. Sign in as an approved merchant on the public sign-in page and open `/merchant/integration`.
2. Click **Create API key** and copy the key. It isn't shown again.
3. Call the order API:

   ```powershell
   Invoke-RestMethod http://localhost:8080/api/v1/orders/<trackingNumber>/status `
     -Headers @{ "X-API-Key" = "<raw key>" }
   ```

4. Revoke the key in the portal. The same call now returns 401.

Interactive documentation is at `/swagger-ui.html` (dev profile), under the `merchant-api` group.

## Tests

| Test | Covers |
| --- | --- |
| `MerchantApiKeyServiceTest` | Create, limit, list, revoke, rotate; `validate` and `getMerchantForKey` for valid, wrong, unknown, malformed, missing, revoked, expired and unapproved keys |
| `ApiKeyAuthenticationFilterTest` | Missing or invalid key → 401 before the controller; valid key → merchant principal; OAuth Bearer token |
| `OAuthTokenControllerTest` | Token for valid client credentials; wrong `client_id` → 401 |
| `OrderApiControllerWebMvcTest` | Order API through the real security chain, including 403 for an unapproved merchant |
| `MerchantApiKeyControllerTest`, `MerchantOrderControllerTest`, `MerchantProfileControllerTest` | Portal endpoints and their validation |
| `MerchantAccessServiceTest`, `MerchantOrderServiceTest`, `MerchantProfileServiceTest` | Approval checks and merchant scoping; each orders filter, combinations, day boundaries and every bad value |
| `MerchantOrderSearchIT` | Orders filters over HTTP against PostgreSQL: each search field, LIKE wildcards, other merchants' orders, day boundaries, combined filters, paging totals, 400 messages |
| `OrdersPage.test.tsx`, `orderFilters.test.ts` | Search delay, status chips, date range, filters and page in the address, page reset, empty and error states |

More integration tests against a real database are planned (milestone M5).
