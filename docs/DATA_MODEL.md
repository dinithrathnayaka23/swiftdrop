# Data model

The database schema implements the team's submitted ER diagram. It is created by
[`V1__init.sql`](../backend/src/main/resources/db/migration/V1__init.sql), and each table has a JPA entity
in the `model` package of the module that owns it.

## Tables by module

| Module | Table | Entity | Notes |
|--------|-------|--------|-------|
| `user` | `users` | `User` | One `role` per user (`Role` enum), `account_status` (`AccountStatus`). `password_hash` is null for Google-only accounts; `google_subject` is null for password-only accounts. |
| `config` | `delivery_zones` | `DeliveryZone` | |
| | `zone_coverage_areas` | `ZoneCoverageArea` | District/city/postal code covered by a zone. |
| | `pricing_rules` | `PricingRule` | Base fee, weight and oversize pricing for a validity period. |
| | `zone_pricing_adjustments` | `ZonePricingAdjustment` | `FLAT` or `PERCENTAGE` adjustment per pickup → delivery zone route. |
| `parcel` | `addresses` | `Address` | Pickup and drop-off addresses with contact details. |
| | `parcel_statuses` | `ParcelStatus` | Reference data, seeded: `BOOKED`, `ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED_ATTEMPT`, `RETURNED`, `CANCELLED`. |
| | `parcels` | `Parcel` | Booked by a customer (`customer_user_id`) or created by a merchant (`merchant_id`). |
| | `parcel_status_history` | `ParcelStatusHistory` | Timeline entries, including proof of delivery and failure reasons. |
| | `delivery_receipts` | `DeliveryReceipt` | One receipt per parcel. |
| `dispatch` | `couriers` | `Courier` | Courier profile for a `COURIER` user (one per user). |
| | `delivery_assignments` | `DeliveryAssignment` | At most one open assignment (`ended_at IS NULL`) per parcel. |
| `merchant` | `merchants` | `Merchant` | Business details and approval workflow (`MerchantApprovalStatus`). |
| | `merchant_api_credentials` | `MerchantApiCredential` | Only a hash of each API key is stored. |
| | `merchant_webhooks` | `MerchantWebhook` | `event_types` is a `TEXT[]`; the signing secret is stored encrypted. |
| | `webhook_deliveries` | `WebhookDelivery` | Delivery attempts with retry tracking; payload is `JSONB`. |
| `notification` | `notifications` | `Notification` | Email / SMS / in-app messages and their delivery status. |
| `reporting` | none | | Reads data through other modules' services or read-only queries. |

## Mapping rules

- **Inside a module**, entities reference each other directly (e.g. `Parcel.pickupAddress` is an `Address`).
- **Across modules**, entities store only the other module's ID (e.g. `Parcel.customerUserId`,
  `DeliveryAssignment.parcelId`). The database still enforces the foreign key, but no module depends on
  another module's entity classes. Load related data through that module's service.
- Hibernate runs with `ddl-auto: validate`: if an entity and the table disagree, the application refuses
  to start. Change the schema with a new Flyway migration and update the entity in the same PR.

## Constraints not shown in the ER diagram

The diagram defines tables, columns and relationships. The migration adds the following:

- `NOT NULL` on required columns; defaults such as `account_status = 'ACTIVE'`, `currency = 'LKR'`,
  `availability_status = 'OFFLINE'`.
- `CHECK` constraints for every status/type column, non-negative amounts, positive weights and dimensions,
  and valid date ranges.
- Uniqueness: email (case-insensitive), `google_subject`, `tracking_number`, `receipt_number`, `zone_name`,
  `registration_no`, `vehicle_registration`, API `key_identifier`, one courier profile per user, one receipt
  per parcel, and a merchant's `external_order_reference`.
- A parcel must belong to a customer or a merchant; a user must have a password or a Google account.
- Indexes on foreign keys and common lookups.
