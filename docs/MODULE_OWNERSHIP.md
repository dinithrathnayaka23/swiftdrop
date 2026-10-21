# Module ownership

> **Status: not assigned yet.** The team has not divided the scope. Once it is agreed,
> fill in the tables below in a PR and update the matching table in the README.

Your **member number** (1-10) is used for:

- branch names: `feature/m<N>-<short-name>`
- Flyway migration versions: `V<N>_<seq>__<description>.sql` (e.g. Member 3 → `V3_001__parcels.sql`)

## Backend modules (`backend/src/main/java/com/swiftdrop/...`)

| Module         | Scope                                                       | Owner (Member #) | Reviewer |
|----------------|-------------------------------------------------------------|------------------|----------|
| `identity`     | Login/signup, JWT issuing, Google OAuth                     | TBD              | TBD      |
| `user`         | Profiles, roles, merchant approval                          | TBD              | TBD      |
| `parcel`       | Booking, fee estimate, tracking                             | TBD              | TBD      |
| `dispatch`     | Courier assignment, status updates, proof of delivery/failure | TBD            | TBD      |
| `merchant`     | Merchant API keys, order API, webhooks                      | TBD              | TBD      |
| `notification` | Event consumers, email / mock SMS, live updates (WebSocket) | TBD              | TBD      |
| `config`       | Zones, pricing rules                                        | TBD              | TBD      |
| `reporting`    | Reports and dashboards data                                 | TBD              | TBD      |
| `common`       | Shared: errors, security, messaging, logging                | Shared, changes need 2 approvals | |

## Cross-cutting

| Area                                   | Owner (Member #) |
|----------------------------------------|------------------|
| Frontend shell, routing, shared components | TBD          |
| DevOps: Docker, CI                     | TBD              |
| QA / test strategy                     | TBD              |

## Shared contracts

| Contract                                   | Who must agree                         |
|--------------------------------------------|----------------------------------------|
| `ParcelStatusChangedEvent` (RabbitMQ)      | Members 3, 6, 9 (as noted in the code; confirm once roles are assigned) |
| `V1__init.sql` (users, roles)              | identity + user owners                 |
| `ErrorResponse` / `ErrorCode`              | everyone (announce changes)            |
