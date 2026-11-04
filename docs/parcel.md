# Parcel module

Owner: Member 4. Package `com.swiftdrop.parcel`; frontend folders `customer-dashboard`, `my-deliveries`,
`admin-parcels` and `shared-parcels`.

The parcel module owns the parcel record, its status and status history, and receipts. Other modules
create, read and move parcels **only** through the two services below. Never use the parcel repositories
or entities from another module.

## Statuses

The six statuses from the final report (section 3.6), in `ParcelStatusCode`:

```
CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
           PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY  -> FAILED
```

- A parcel moves forward one step at a time. `DELIVERED` and `FAILED` are end states.
- Courier assignment is not a status: it lives in the dispatch module (`delivery_assignments`).
- Use `ParcelStatusCode.canChangeTo(next)`, `nextStatuses()` and `isTerminal()` instead of your own rules,
  for example to decide which buttons the courier app shows.
- Need another status or transition? Ask Member 4: the values are shared by every module.

Parcel types (`ParcelType`): `DOCUMENT`, `PARCEL`, `FRAGILE`, `ELECTRONICS`, `PERISHABLE`.

## Creating and finding parcels: `ParcelService`

| Method | Use it for |
| ------ | ---------- |
| `createParcel(NewParcel)` → `ParcelDetails` | Booking (Member 3) and merchant orders (Member 9). Saves the two addresses, the parcel (status `CREATED`) and the first history entry. Throws `ConflictException` if the merchant already used this `externalOrderReference`. |
| `getParcel(UUID)` → `ParcelDetails` | One parcel. Throws `NotFoundException`. |
| `findAllByIds(Collection<UUID>)` → `List<ParcelDetails>` | Many parcels in one query, e.g. a courier's assignments. Unknown ids are skipped. |
| `findByTrackingNumber(String)` → `Optional<ParcelDetails>` | Public tracking (Member 5). Ignores case and spaces. |
| `findByMerchantOrderReference(UUID merchantId, String reference)` → `Optional` | Merchant order API look-ups (Member 9). |
| `listByCustomer(UUID, Pageable)`, `listByMerchant(UUID, Pageable)`, `listByStatus(ParcelStatusCode, Pageable)` → `Page<ParcelDetails>` | Lists, newest first unless you pass a sort. `listByStatus(CREATED)` gives the parcels waiting for a courier (Member 6 removes those that already have an open assignment). |
| `countByStatus()`, `countByStatusForCustomer(UUID)`, `countByStatusForMerchant(UUID)` → `Map<ParcelStatusCode, Long>` | Dashboards. Every status is present, 0 when there are none. |

`NewParcel` rules (checked when you build it; a mistake throws `IllegalArgumentException`):

- exactly one owner: `customerUserId` **or** `merchantId`;
- `externalOrderReference` only for merchant parcels;
- `pickupAddress`, `dropoffAddress`, `parcelType`, `weightKg` (> 0) and `quotedFee` (≥ 0) are required.

Validate the user's input in your own request DTO first; these checks only catch programming errors.

`ParcelDetails` contains personal data (names, phone numbers, addresses). Check that the caller may see
the parcel, then map it to your own response with only the fields that caller needs. The public tracking
page must never show phone numbers, exact addresses or proof photos.

## Changing the status: `ParcelStatusService`

```java
statusService.changeStatus(parcelId, StatusChange.to(ParcelStatusCode.PICKED_UP, courierUserId, "Collected"));
statusService.changeStatus(parcelId, StatusChange.delivered(courierUserId, "Kamala Silva", proofUrl, null));
statusService.changeStatus(parcelId, StatusChange.failed(courierUserId, "Recipient not available", null));
List<StatusHistoryEntry> timeline = statusService.getHistory(parcelId); // oldest first
```

- **Check permission first.** The service does not know who may change a parcel: the courier app checks
  the courier is assigned to it (Member 7), dispatch checks the admin (Member 6).
- `DELIVERED` needs `recipientName` or `proofFileUrl`; `FAILED` needs `failureReason`. They are refused
  for any other status (`BadRequestException`, 400).
- A change the diagram doesn't allow throws `ConflictException` (409). Nothing is saved or published.
- The parcel row is locked during the change, so two updates of one parcel can't overlap.

## Status event (RabbitMQ)

After each committed change, `ParcelStatusChangedEvent` (in `common.messaging.event`) goes to exchange
`swiftdrop.parcel.events` with routing key `parcel.status.changed`.

| Field | Meaning |
| ----- | ------- |
| `eventId` | Unique per event. Delivery is at least once: skip ids you have already handled. |
| `eventType`, `schemaVersion` | `PARCEL_STATUS_CHANGED`, `1` |
| `parcelId`, `trackingNumber` | The parcel |
| `merchantId` | Owning merchant, or `null` for a customer booking (webhooks: Member 9) |
| `previousStatus`, `newStatus` | Status codes as strings |
| `statusVersion` | 1 for CREATED, then +1 per change. Ignore an event older than one you already have. |
| `statusHistoryId` | The `parcel_status_history` row (for `notifications` / `webhook_deliveries`) |
| `changedByUserId` | Who made the change, or `null` for a system change |
| `occurredAt` | When the change was saved |

The event never contains personal data; look up what you need by id. Parcel creation does not publish
an event.

## REST endpoints

All under `/api/v1`. Lists use Spring's paged JSON: `{ "content": [...], "page": { "size", "number",
"totalElements", "totalPages" } }`, newest first, `page` from 0, `size` 1–100 (default 10).

| Endpoint | Role | Notes |
| -------- | ---- | ----- |
| `GET /customer/parcels/counts` | CUSTOMER | Parcels per status |
| `GET /customer/parcels?search&status&bookedFrom&bookedTo&page&size` | CUSTOMER | Own parcels only. `search` matches tracking number, delivery city or order reference. Dates are `yyyy-MM-dd`, whole days in Sri Lanka time. |
| `GET /customer/parcels/by-tracking-number/{trackingNumber}` | CUSTOMER | Details and history. 404 if the parcel isn't the caller's. |
| `GET /customer/parcels/{parcelId}/receipt` | CUSTOMER | PDF download. The first download records the receipt number in `delivery_receipts`. |
| `GET /admin/parcels/counts` | DISPATCHER_ADMIN | Counts per tab: `ALL`, `UNASSIGNED`, `ASSIGNED`, `IN_TRANSIT`, `DELIVERED`, `FAILED` |
| `GET /admin/parcels?tab&search&status&area&createdFrom&createdTo&page&size` | DISPATCHER_ADMIN | `search` matches tracking number, recipient or merchant name; `area` is the delivery city. |
| `GET /admin/parcels/{parcelId}` | DISPATCHER_ADMIN | Details and history |

Swagger UI (dev profile) lists the request and response fields: `http://localhost:8080/swagger-ui.html`.

## Database

Tables owned by this module: `parcels`, `parcel_statuses`, `parcel_status_history`, `delivery_receipts`.
The `addresses` table is written only through `createParcel`.

| Migration | Change |
| --------- | ------ |
| `V4_001__parcel_statuses_report_flow.sql` | The six report statuses (replaces the nine from V1) |
| `V4_002__parcels_parcel_type.sql` | `parcels.parcel_type` |

## For the dispatch module: `DispatchStateLookup`

The admin Parcels screen shows whether a CREATED parcel has a courier ("Unassigned" / "Assigned"). The
parcel module can't read `delivery_assignments`, so dispatch (Member 6) tells it through this interface:

```java
@Service
@RequiredArgsConstructor
class OpenAssignments implements DispatchStateLookup {
    private final DeliveryAssignmentRepository assignments;

    @Override
    public Set<UUID> parcelsWithOpenAssignment() {
        return assignments.findParcelIdsByEndedAtIsNull(); // your own query
    }
}
```

Once that bean exists, the admin tabs, counts and each row's `dispatchState` use it; nothing else needs
to change. Until then every CREATED parcel counts as unassigned and the "Assigned" tab is empty.

## Not done yet (waiting for other modules)

- **Unassigned / Assigned:** needs the `DispatchStateLookup` bean from dispatch (Member 6), see above.
- **Merchant names** on the admin screens come from `MerchantService.listByStatus(APPROVED)`. A look-up by
  ids in the merchant module (Member 2) would be faster.
- **Payments, service type, recipient email, parcel notes:** not in the schema; the screens show "—".
- **Receipts** use the built-in Helvetica font, so Sinhala and Tamil names are not printed.
