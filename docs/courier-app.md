# Courier app (delivery module)

Owner: Member 7. Package `com.swiftdrop.delivery`; frontend folder `courier-app`. Two small additions
live in the dispatch package, each a new file: the `CourierSelfApi` for the profile card and the
dev-only sample delivery helper (see below).

The courier app is what a courier uses on their phone: today's deliveries, moving a parcel through
its steps, proof of delivery, failed attempts, scanning a label and the history of finished
deliveries. It **owns no parcel or assignment data**. It changes parcels only through the parcel
module's `ParcelStatusService`, reads them through `ParcelService`, asks dispatch who a parcel belongs
to through `DispatchApi`, and keeps only its own records: proofs of delivery and failure reasons.

## Screens

The courier signs in at `/courier/login` (a courier account can't use the main sign-in page). The
app has a bottom tab bar on every screen size: **Today's Deliveries**, **Scan Parcel**, **Completed**,
**Account** (the Account page belongs to the auth feature).

| Screen | Path | What it does |
| ------ | ---- | ------------ |
| Today's Deliveries | `/courier` | Profile card (vehicle, areas, Online / On break / Offline), the counts, and the open deliveries, oldest assignment first. Refreshes every minute and when the app comes back into view. |
| Delivery Details | `/courier/deliveries/{parcelId}` | Both stops with a call button, the parcel, the next steps and the history. |
| Update Status | `/courier/deliveries/{parcelId}/status?status=…` | Picked up, in transit, out for delivery, with an optional note. |
| Proof of Delivery | `/courier/deliveries/{parcelId}/proof` | Who received it (filled with the recipient's name), an optional photo from the back camera, a note. |
| Failed Delivery | `/courier/deliveries/{parcelId}/failed` | A reason (some need a note) and a confirmation. |
| Scan Parcel | `/courier/scan` | Camera scanning where the browser supports it (the Barcode Detection API, e.g. Chrome on Android), and typing the tracking number, which always works. |
| Completed | `/courier/completed` | Deliveries and failed attempts, newest first, with outcome and date filters, paging and the proof photo. |

The buttons on a delivery come from its `allowedActions`, worked out on the server from
`ParcelStatusCode.nextStatuses()`, keeping only the steps a courier may take (`CREATED` and `FAILED`
are the dispatcher's).

## The delivery as the courier sees it

```
assigned (CREATED) -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED       (proof of delivery)
                      PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY  -> FAILED_ATTEMPT  (failed delivery form)
```

- A courier can only report a failed attempt **after pickup**. `FAILED_ATTEMPT` is not the end: the
  dispatcher retries the parcel (back to `CREATED`, unassigned) or closes it (`FAILED`).
- After `DELIVERED` or `FAILED_ATTEMPT`, dispatch ends the courier's assignment (its status listener,
  a moment later). The delivery then leaves Today's Deliveries and opening it gives 404; it shows in
  Completed instead.
- A retried parcel can be given to a courier again and fail again: a parcel can have several
  failure rows.
- Every action is one transaction. If anything fails (a photo that isn't an image, a step the
  parcel can't take now), nothing is saved and no status event is published.

## Ownership

A courier only sees and changes parcels with an **open assignment to them**
(`DispatchApi.isAssignedTo`). Any other parcel, including one that doesn't exist, is
"Delivery not found" (**404, not 403**), so a courier can't learn which parcels or tracking numbers
exist. Completed Deliveries and the proof photo use the courier's own records instead
(`courier_user_id`), because the assignment has ended by then.

## REST endpoints

All under `/api/v1`, `COURIER` role unless noted. Errors use the shared `ErrorResponse`.

| Endpoint | Notes |
| -------- | ----- |
| `GET /courier/deliveries/today` | `{counts: {total, assigned, inProgress, completedToday}, deliveries: [...]}`. `completedToday` counts deliveries since midnight in the admin's time zone (failed attempts don't count). |
| `GET /courier/deliveries/{parcelId}` | The delivery with its timeline. 404 when it isn't the courier's. |
| `GET /courier/deliveries/by-tracking-number/{tn}` | The scanner's look-up. Case and spaces are ignored. 404 as above. |
| `POST /courier/deliveries/{parcelId}/status` `{status, note?}` | `PICKED_UP`, `IN_TRANSIT` or `OUT_FOR_DELIVERY`; note max 500. 400 for `DELIVERED` or a failure (own endpoints), 404 not theirs, 409 when the parcel can't take that step now (e.g. a double tap). Returns the updated delivery. |
| `POST /courier/deliveries/{parcelId}/deliver` | `multipart/form-data`: `recipientName` (required, max 120), `note?` (max 500), `photo?` (JPEG or PNG). 400 for a bad name or photo, 404, 409 when not out for delivery, 413 when the upload is too large. |
| `GET /courier/failure-reasons` | `[{code, label, noteRequired}]`, in display order. |
| `POST /courier/deliveries/{parcelId}/fail` `{reasonCode, note?}` | Records `FAILED_ATTEMPT`. 400 for an unknown reason or a missing required note, 404, 409 before pickup. The history's failure reason is the label, plus `: ` and the note when there is one. |
| `GET /courier/deliveries/completed?from&to&outcome&page&size` | Spring's paged JSON, newest first. `from`/`to` are inclusive dates (`yyyy-MM-dd`) in the admin's time zone, default the last 7 days; `outcome` is `DELIVERED` or `FAILED_ATTEMPT`; `size` 1-100 (default 20). |
| `GET /courier/profile` | The profile card: name, contact, vehicle, `status`, `active`, service area names and today's workload. 404 when the account has no courier profile. |
| `PATCH /courier/profile/status` `{status}` | `ONLINE`, `ON_BREAK` or `OFFLINE`. 409 when a deactivated courier asks to go online. Going on break or offline keeps the courier's open deliveries (the dispatcher reassigns them). |
| `GET /proofs/{proofId}/photo` | **Any signed-in user**, checked per caller: the admin, the courier who took it, or the customer whose parcel it is; everyone else gets 404. `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`, shown inline. |

Because the photo endpoint needs the sign-in token, a page can't show it with a plain `<img src>`:
fetch it through the API client (as a `Blob`) and show an object URL, as the courier app's
`ProofPhotoViewer` does.

## Proof photos

- `ProofStorage` / `LocalFileProofStorage`: files under `PROOF_STORAGE_DIR` (default `./data/proofs`)
  at `yyyy/MM/<uuid>.jpg`. The folder writes its own `.gitignore` (`*`): photos are personal data and
  must never be committed.
- Every upload is checked and redrawn, never stored as sent: the first bytes must be a JPEG or PNG
  signature; the image size is read before decoding and anything over 40 megapixels is refused; the
  picture is redrawn on a plain RGB canvas at most 1600 px and written as a new JPEG. Nothing of the
  original file survives, so EXIF data such as the GPS location is gone.
- The app also shrinks the photo in the browser (canvas, at most 1600 px) before uploading, so uploads
  are a few hundred KB and fit Spring's default 1 MB multipart limit.
- If the delivery's transaction rolls back, the stored file is deleted again.
- The parcel history keeps `proofFileUrl = /proofs/{proofId}/photo` (relative to `/api/v1`).

## For the dispatch module: `CourierSelfApi`

New files in `dispatch` (Member 7, using Member 6's services; nothing of theirs was changed):

| Type | Purpose |
| ---- | ------- |
| `dispatch.api.CourierSelfApi` | `profile(courierUserId)` and `changeStatus(courierUserId, CourierStatus)`, both `Optional` (empty when the account has no courier profile). |
| `dispatch.api.CourierProfile`, `dispatch.api.CourierStatus` | The profile record and the availability values other modules see. |
| `dispatch.service.CourierSelfService` | The implementation: `DispatchApi.findCourierId`, then `CourierService.get` / `changeAvailability`, so dispatch keeps its own rules. |

**To agree with Member 6:** couriers may switch themselves Online / On Break / Offline.

## Database

| Migration | Tables |
| --------- | ------ |
| `V7_001__failure_reasons.sql` | `failure_reasons` (`reason_code` PK, `label`, `note_required`, `display_order`, `is_active`), seeded with RECIPIENT_UNAVAILABLE, WRONG_ADDRESS, REFUSED, ACCESS_ISSUE, DAMAGED (note required), OTHER (note required). `delivery_failures` (`failure_id`, `parcel_id`, `status_history_id` unique, `courier_user_id`, `reason_code`, `note`, `recorded_at`); no unique constraint on `parcel_id` (retries). |
| `V7_002__delivery_proofs.sql` | `delivery_proofs` (`proof_id`, `parcel_id`, `status_history_id` unique, `courier_user_id`, `recipient_name`, `photo_storage_key`, `photo_content_type`, `photo_size_bytes`, `photo_sha256`, `captured_at`). A check keeps the four photo columns all set or all empty. |

Both store the courier's **account id** (`users.user_id`), the id the sign-in token carries, so they
don't depend on dispatch's `couriers` table. Indexes on `(courier_user_id, time)` serve Completed
Deliveries. Reasons can only be switched off in the database (`is_active`); there's no admin screen.

## Trying it locally

- **Sample data:** in `npm run dev` without `VITE_USE_MOCK_API`, every screen uses the in-browser mock
  (`courier-app/mock/mockCourierApi.ts`): five deliveries at every step, a few completed ones and a
  profile. Signing in always needs a real courier account.
- **Real backend:** set `VITE_USE_MOCK_API=false` in `frontend/.env.local`. Add a courier from the
  admin Couriers page (the dispatcher sets the password), then sign in at `/courier/login`.
- **Dev profile only:** `POST /api/v1/courier/dev/sample-delivery` (COURIER) books a sample parcel and
  assigns it to the signed-in courier, setting them Online first. In development builds Today's
  Deliveries shows an **Add sample delivery** button for it. It lives in dispatch
  (`DevCourierDemoService`), because only dispatch can assign; a courier whose service areas don't
  cover Colombo gets dispatch's 409.
- The camera only works on a secure page (`https://` or `localhost`); on a laptop, type the number.

## Tests

- Unit tests per service, controller and request record; `LocalFileProofStorageTest` covers resizing,
  metadata removal, refused files and deletion on rollback.
- `CourierDeliveriesIT`: each endpoint's cases (ownership, 400/404/409, filters, paging, photo access).
- `CourierDeliveryIT`: the whole journey through the real API, from an assigned parcel to DELIVERED
  with history, proof and the assignment ended as `COMPLETED`; another courier's parcel; a failed
  attempt without a reason; the photo for the owning customer only; the profile card.

## Not done yet (waiting for other modules)

- **Member 10:** a `proof_data` volume at `/app/data/proofs`, `PROOF_STORAGE_DIR`, the folder created
  and owned by `swiftdrop` in the backend Dockerfile, and `client_max_body_size 6m;` in nginx's `/api/`
  block. Until then, photo uploads work when the backend runs with `mvnw`, not in the full Docker stack.
- **Member 1 (optional):** a `PAYLOAD_TOO_LARGE` error code; an oversized upload already gets 413, with
  code `BAD_REQUEST`.
- **Member 4 (nice to have):** a QR code of the tracking number on the receipt. The scanner already
  reads a tracking link inside a QR code (`…/track/SD…` or `?trackingNumber=`).
- **Member 6:** confirm couriers may change their own availability (`CourierSelfApi`). "Today" in the
  courier app follows the admin's time-zone setting, while dispatch's workload uses `Asia/Colombo`; they
  differ only if the admin changes the setting.
- Customers can't see the proof photo anywhere yet; a customer screen would load it as described above.
