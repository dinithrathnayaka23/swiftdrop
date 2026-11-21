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

### Suspension

The admin suspends a merchant from **User Management** (the contact person's account).
`MerchantAccountStatusListener` keeps the merchant in step once that change is committed:

| Account change | Merchant |
| --- | --- |
| → `SUSPENDED` or `DEACTIVATED` | `APPROVED` → `SUSPENDED` |
| → `ACTIVE` | `SUSPENDED` → `APPROVED` |
| Anything else | Unchanged (pending and rejected merchants are decided through merchant approval) |

So a suspension takes effect at once: the portal and the order API (API keys and OAuth tokens)
answer 403 `ACCOUNT_DISABLED`, even for an access token issued before the suspension.

In the browser, the API client reports a signed-in 403 with `ACCOUNT_DISABLED` or
`ACCOUNT_PENDING_APPROVAL` (`setAccountBlockedHandler`). `MerchantAccessGate`, around the
merchant area, then replaces the whole portal with one **account not active** page (sign out,
check again, contact support) instead of an error card on every screen.

## Screens

| Path | Screen | Data |
| --- | --- | --- |
| `/merchant` | Dashboard | Profile, order counts per status, 5 most recent orders, integration status (API keys and webhook) |
| `/merchant/orders` | Orders | Paged list, newest first. Search box, status chips and a creation date range. The filters and the page are kept in the address (`?search=&status=&from=&to=&page=`), so a filtered list can be bookmarked or shared. A changed filter goes back to page 1. |
| `/merchant/orders/:trackingNumber` | Order Details | Addresses, parcel, fee, progress timeline, failure reason. Receipt PDF download. Live status: follows the parcel's public stream (`/public/tracking/{trackingNumber}/stream`, Member 5's `useLiveUpdates`), shows a newer status at once and then reloads the order quietly; it also reloads after a reconnect. |
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
| GET | `/api/v1/merchant/orders/{trackingNumber}/receipt` | The order's receipt PDF (`receipt-<trackingNumber>.pdf`, `Cache-Control: no-store`), drawn by `ReceiptService.receiptForMerchant` with the current status. The first download records the receipt number. 404 for another merchant's order, a customer booking or an unknown number. |
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
Optional<ValidatedApiKey> validateCredential(UUID merchantId, UUID apiCredentialId); // key behind an OAuth token
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
  order API. Each call with the token checks its key again (`validateCredential`), so the token
  stops working as soon as the key is revoked (401) or the merchant is suspended (403).
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
| `MerchantApiKeyServiceTest` | Create, limit, list, revoke, rotate; `validate` and `getMerchantForKey` for valid, wrong, unknown, malformed, missing, revoked, expired and unapproved keys; `validateCredential` for active, revoked, expired, another merchant's and unapproved |
| `ApiKeyAuthenticationFilterTest` | Missing or invalid key → 401 before the controller; valid key → merchant principal; OAuth Bearer token, and a revoked key's token → 401 |
| `OAuthTokenControllerTest` | Token for valid client credentials; wrong `client_id` → 401 |
| `OrderApiControllerWebMvcTest` | Order API through the real security chain, including 403 for an unapproved merchant |
| `MerchantApiKeyControllerTest`, `MerchantOrderControllerTest`, `MerchantProfileControllerTest` | Portal endpoints and their validation |
| `MerchantAccessServiceTest`, `MerchantOrderServiceTest`, `MerchantProfileServiceTest` | Approval checks and merchant scoping; each orders filter, combinations, day boundaries and every bad value |
| `MerchantOrderSearchIT` | Orders filters over HTTP against PostgreSQL: each search field, LIKE wildcards, other merchants' orders, day boundaries, combined filters, paging totals, 400 messages |
| `OrdersPage.test.tsx`, `orderFilters.test.ts` | Search delay, status chips, date range, filters and page in the address, page reset, empty and error states |
| `MerchantOrderReceiptIT`, `ReceiptServiceTest` | Receipt download: own order PDF recorded once, another merchant's order, customer booking, unknown number, unapproved merchant, customer role |
| `OrderDetailsPage.test.tsx`, `useLiveOrder.test.ts` | Live status (newer update shown at once, stale ignored, quiet reload, reconnect, failed reload, paused), receipt download and its error |
| `MerchantAccountStatusListenerTest`, `MerchantSuspensionIT` | Merchant status follows its account; suspending blocks the portal and API key at once (old access token included), reactivating restores them |
| `MerchantAccessGate.test.tsx`, `AccountNotActivePage.test.tsx`, `client.test.ts` | The page replaces error cards, sign out, check again, pending copy; which 403s the API client reports |

### Integration tests

They run with `mvn verify` against real PostgreSQL, RabbitMQ and Redis (Testcontainers, so Docker
must be running). Shared set-up is in `MerchantIntegrationTestBase`.

| Test | Covers |
| --- | --- |
| `MerchantPortalIT` | Profile view and update (and a refused update that changes nothing), dashboard counts, orders list and details with the timeline, another merchant's order and receipt → 404, who may use the portal (401, 403 for a customer, pending, rejected, no merchant), page limits |
| `ApiKeyIT` | Key shown once and stored only as a hash, use on the order API and `last_used_at`, revoke (→ 401) and revoking twice, rotate, the two-key limit, another merchant's key, bad keys, OAuth token issued and refused, a token stops working when its key is revoked or the merchant is suspended |
| `MerchantOrderApiIT` | Create an order with an API key or OAuth token (fee from the pricing rule, no personal data in the response), follow its status, see it in the portal, a repeated order reference returns the same order, another merchant → 404, validation, missing key, suspended merchant |
| `WebhookIT` | A status change reaches the merchant's HTTPS endpoint through RabbitMQ, signed (`X-SwiftDrop-Signature`) and without personal data; the delivery log; a failure is retried after the backoff and then delivered; after the last retry it's `FAILED` and the merchant can retry it; test event; secret rotation; endpoint rules. The endpoint is a local HTTPS server with a certificate made by the test. |
| `MerchantOrderSearchIT`, `MerchantOrderReceiptIT`, `MerchantSuspensionIT` | Orders filters, receipt download and suspension (see above) |
