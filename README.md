<div align="center">

# SwiftDrop

**Parcel delivery and courier management, from booking to doorstep.**

[![CI](https://github.com/dinithrathnayaka23/swiftdrop/actions/workflows/ci.yml/badge.svg)](https://github.com/dinithrathnayaka23/swiftdrop/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-4-FF6600?logo=rabbitmq&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Helm-326CE5?logo=kubernetes&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Overview](#overview) •
[Architecture](#architecture) •
[Getting started](#getting-started) •
[Kubernetes](#run-on-kubernetes) •
[Testing](#testing) •
[Docs](#documentation) •
[Contributing](#contributing)

</div>

---

## Overview

SwiftDrop is a web platform that connects everyone involved in getting a parcel from A to B:

| Who | What they do |
|-----|--------------|
| **Customers** | Book deliveries, get an instant fee estimate and follow their parcels in real time |
| **Merchants** | Push orders through a REST API with API keys and receive status updates via webhooks |
| **Dispatchers / Admins** | Assign parcels to couriers, manage zones and pricing, approve merchants and view reports |
| **Couriers** | See their jobs, update delivery status and record proof of delivery or failure reasons |
| **Anyone** | Track a parcel publicly with its tracking number |

Status changes flow through RabbitMQ, so notifications (email, mock SMS, in-app), live tracking updates and
merchant webhooks happen asynchronously without slowing down the core workflow.

### Features

| Area | What's built |
|------|--------------|
| **Customer** | Sign-up with email confirmation, **Sign in with Google** (OAuth 2.0), booking with a fee estimate before confirming, live tracking (SSE), delivery history, PDF receipt, notification preferences |
| **Public tracking** | Mobile-friendly page at `/track/<tracking number>`, updating live |
| **Merchant** | Registration with admin approval, API keys and OAuth 2.0 client credentials, order API, signed webhooks with automatic retry, dashboard of orders and their states |
| **Courier** | Phone-first app: today's deliveries, status updates with a note, recipient name or photo proof, failure with a reason |
| **Dispatcher / admin** | Unassigned parcels and assignment, courier workload, users, couriers, merchants, service areas and pricing rules, reports (deliveries per day, success/failure rate), live delivery tracking, system settings |
| **AI assistant** (bonus) | Customer chatbot that answers "Where is my parcel?" or "How much to send 2 kg to Kandy?" by calling backend services as tools ([docs/assistant.md](docs/assistant.md)) |
| **Kubernetes** (bonus) | Helm chart with ConfigMap, Secret, Services, StatefulSets and a Traefik Ingress ([docs/kubernetes.md](docs/kubernetes.md)) |

## Architecture

SwiftDrop is a **layered modular monolith**: a single Spring Boot application divided into well-bounded
feature modules, with a separate React single-page application in front of it.

```mermaid
flowchart LR
    user([Browser]) --> fe["<b>frontend</b><br/>React SPA · Nginx"]
    fe -- "/api/v1" --> be

    subgraph be["<b>backend</b> · Spring Boot"]
        direction TB
        mods["identity · user · usermanagement · booking · pricing<br/>parcel · dispatch · delivery · tracking · realtime<br/>merchant · notification · reporting · config · settings · assistant"]
        common["common: security · errors · messaging · logging"]
        mods --- common
    end

    be <--> db[("PostgreSQL<br/>schema via Flyway")]
    be <-- "parcel events" --> mq[["RabbitMQ"]]
    be <-- "sessions · rate limits" --> cache[("Redis")]
```

**Design principles**

- **Modules own their data.** Each module exposes services (and events); other modules never reach into its repositories or entities.
- **Thin controllers, rich services.** Controllers validate and map DTOs; business rules live in services.
- **Secure by default.** Short-lived JWT access tokens, a refresh token only in an httpOnly cookie backed by a server-side session in Redis (so sign-out takes effect immediately), rate limits on every sign-in endpoint, role-based method security, and every route private unless explicitly public.
- **Asynchronous side effects.** Parcel status changes are published to RabbitMQ; notifications and webhooks consume them, with a dead-letter queue for failures.
- **Schema as code.** Flyway migrations are the single source of truth; Hibernate only validates.
- **Privacy first.** No stack traces or personal data in API errors, and personal data is never logged.

### Backend modules

| Module | Responsibility |
|--------|----------------|
| `identity` | Sign-up, sign-in, email confirmation, password reset, JWT and sessions, Google sign-in |
| `user`, `usermanagement` | Profiles, roles and account status, admin user management |
| `booking`, `pricing` | Customer bookings, fee estimates, pricing rules |
| `parcel` | Parcel lifecycle, status history, receipts, status events |
| `dispatch` | Assignment, courier management and workload, failed deliveries |
| `delivery` | Courier app API: today's deliveries, status updates, proof photos, failures |
| `tracking`, `realtime` | Public tracking, live streams (SSE) for customers and the admin map |
| `merchant` | Merchant portal, approval, API keys, OAuth client credentials, order API, webhooks with retry |
| `notification` | Status-event consumer: email, mock SMS and in-app notifications, preferences |
| `reporting` | Deliveries per day, success/failure rate and other reports |
| `config`, `settings` | Service areas, system settings and maintenance mode |
| `assistant` | AI delivery assistant (Gemini, with OpenRouter, Grok and offline fallbacks) |

## Tech stack

| Area | Technologies |
|------|--------------|
| **Backend** | Java 25, Spring Boot 4.1 (Web MVC, Validation, Data JPA, Security, OAuth2 Client, AMQP, WebSocket, Mail, Actuator), springdoc-openapi, Lombok |
| **Frontend** | React 19, TypeScript 6, Vite 8, Tailwind CSS 4, React Router 8, Axios |
| **Data & messaging** | PostgreSQL 18, Flyway, RabbitMQ 4, Redis 8 |
| **Testing** | JUnit 6, Mockito, Spring Boot Test, Spring Security Test, Testcontainers, JaCoCo, Jest, React Testing Library |
| **Tooling** | Maven Wrapper, ESLint, Prettier, Docker Compose, Kubernetes, Helm, Traefik, GitHub Actions |

## Getting started

### Prerequisites

| Tool | Version | Required for |
|------|---------|--------------|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Compose v2+ | Running the stack and integration tests |
| [JDK](https://adoptium.net/) | 25 (LTS) | Backend development |
| [Node.js](https://nodejs.org/) | 24 LTS (22.22+) | Frontend development |

Maven is optional: the repository ships with the Maven Wrapper (`./mvnw`).
PostgreSQL, RabbitMQ and Redis run in Docker; nothing else needs to be installed.

### Run the full stack

```bash
git clone https://github.com/dinithrathnayaka23/swiftdrop.git
cd swiftdrop
cp .env.example .env            # PowerShell: Copy-Item .env.example .env
docker compose up --build
```

Once all containers report **healthy**:

| Service | URL |
|---------|-----|
| Web app | http://localhost:3000 |
| API health | http://localhost:8080/api/v1/health |
| API docs (Swagger UI, dev profile) | http://localhost:8080/swagger-ui.html |
| Actuator health | http://localhost:8080/actuator/health |
| RabbitMQ management | http://localhost:15672 |
| Mailpit (emails sent by the app) | http://localhost:8025 |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |

Credentials for PostgreSQL, RabbitMQ and Redis are the ones in your `.env`.

> [!TIP]
> **First sign-in:** SwiftDrop has a single admin whose credentials are `ADMIN_EMAIL` / `ADMIN_PASSWORD` in `.env`
> (required; synced on every startup, so edit `.env` and restart to change them). Sign in at `/admin/login`. Couriers sign in at `/courier/login` (built for phones).
> Customers and merchants register from the web app; merchants can sign in once an admin approves them.
> Password reset and approval emails appear in Mailpit. Parcel tracking reads real parcels; with the
> backend on the `dev` profile, create and advance sample parcels as shown in [docs/tracking.md](docs/tracking.md).

Stop everything with `docker compose down` (add `-v` to also delete the database and broker data).

### Run on Kubernetes

A Helm chart in `infra/helm/swiftdrop` deploys the same stack (ConfigMap, Secret, Deployments, StatefulSets
with volumes, Services and a Traefik Ingress). On a local kind cluster:

```bash
infra/k8s/deploy.sh --create-kind   # then open http://localhost
```

Setup, Docker Desktop, real clusters and limits: [docs/kubernetes.md](docs/kubernetes.md).

## Local development

For day-to-day work, run the infrastructure in Docker and the applications on your machine:

```bash
docker compose up -d postgres rabbitmq redis
```

**Backend** (http://localhost:8080). The `dev` profile automatically reads the repository's `.env`:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # Windows: mvnw.cmd
```

**Frontend** (http://localhost:5173, with `/api` proxied to the backend):

```bash
cd frontend
npm install
npm run dev
```

<details>
<summary><b>Prefer hot reload inside Docker?</b></summary>

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
```

Runs the Vite dev server with HMR on port 5173 and the backend from source.
Restart the `backend` service to pick up Java changes.

</details>

## Configuration

All configuration is supplied through environment variables; see [`.env.example`](.env.example) for the full list.
Secrets are never committed: `.env` is git-ignored.

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `dev` locally (enables Swagger, reads `.env`) |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_URL` | Database connection |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Message broker connection |
| `JWT_SECRET`, `JWT_EXPIRY` | Token signing key (≥ 32 chars) and access token lifetime in seconds (default 900) |
| `API_KEY_PEPPER` | Secret mixed into merchant API key hashes (≥ 32 chars); changing it invalidates every issued key |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis: sign-in sessions, rate limits, reset-email cooldown |
| `AUTH_COOKIE_SECURE` | Session cookie only over HTTPS (browsers allow it on `http://localhost`) |
| `RATE_LIMIT_ENABLED` | Per-IP and failed-sign-in limits on `/api/v1/auth` |
| `GOOGLE_CLIENT_ID` | Sign in with Google (OAuth Client ID; empty hides the button) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Outgoing email (Mailpit in Docker) |
| `FRONTEND_URL` | Allowed CORS origin(s), comma-separated |
| `VITE_USE_MOCK_API` | Sample data for the booking, pricing and parcel screens (sign-in and public tracking always use the backend) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_FULL_NAME` | First admin account, created on startup if it doesn't exist |
| `FRONTEND_BASE_URL`, `PASSWORD_RESET_EXPIRY` | Web app address used in email links; reset link lifetime (seconds) |
| `REFRESH_TOKEN_EXPIRY_REMEMBER_ME`, `REFRESH_TOKEN_EXPIRY_SESSION` | Session length with / without "Remember me" (seconds; default 30 days / 1 day) |
| `WEBHOOK_SECRET_KEY` | AES-GCM key material for encrypting merchant webhook signing secrets (at least 32 characters) |
| `WEBHOOK_ALLOW_PRIVATE_HOSTS` | Allow private or localhost webhook targets for development only (default `false`) |
| `WEBHOOK_RETRY_DELAY_1` ... `WEBHOOK_RETRY_DELAY_5` | Webhook retry backoff delays (defaults: `1m`, `5m`, `30m`, `2h`, `6h`) |

> [!IMPORTANT]
> The values in `.env.example` are for local development only. Generate a strong `JWT_SECRET` and
> `API_KEY_PEPPER` (`openssl rand -base64 48`) and unique passwords for any shared or hosted environment.

## Merchant order API & webhooks

Approved merchants can create orders with an API key and receive asynchronous, signed parcel status
updates. Use `X-API-Key` on order requests, or exchange OAuth 2.0 client credentials at
`POST /api/v1/oauth/token` for a short-lived Bearer token.

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/v1/orders` | Create a merchant order |
| `GET` | `/api/v1/orders/{trackingNumber}` | Read an order owned by the merchant |
| `GET` | `/api/v1/orders/{trackingNumber}/status` | Read the current order status |
| `GET` / `PUT` | `/api/v1/merchant/webhook` | Read or update webhook settings |
| `POST` | `/api/v1/merchant/webhook/rotate-secret` | Rotate the signing secret (shown once) |
| `GET` | `/api/v1/merchant/webhook/deliveries` | List delivery attempts |
| `POST` | `/api/v1/merchant/webhook/deliveries/{id}/retry` | Retry a failed delivery |
| `POST` | `/api/v1/merchant/webhook/test` | Send a signed test event |

Example order request:

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "externalOrderReference": "STORE-10042",
    "pickupAddress": {
      "contactName": "SwiftDrop Warehouse",
      "contactPhone": "+94112223344",
      "addressLine1": "10 Main Street",
      "city": "Colombo",
      "district": "Colombo"
    },
    "dropoffAddress": {
      "contactName": "Jane Customer",
      "contactPhone": "+94771234567",
      "addressLine1": "25 Lake Road",
      "city": "Kandy",
      "district": "Kandy"
    },
    "parcelType": "PARCEL",
    "weightKg": 2
  }'
```

Webhook payloads contain the event ID, event type, tracking number, external order reference,
previous status, new status and occurrence time; they do not contain personal address or contact data.
To verify a delivery, read the raw request body and compute lowercase hexadecimal HMAC-SHA256 using
the signing secret over:

```text
X-SwiftDrop-Timestamp + "." + raw_request_body
```

Compare the result with `X-SwiftDrop-Signature` and reject stale timestamps to prevent replay attacks.
The signing secret is displayed only when webhook settings are first created or rotated.

## Testing

```bash
# Backend: unit tests, Testcontainers integration tests and coverage (requires Docker)
cd backend
./mvnw verify

# Frontend: lint, unit tests and production build
cd frontend
npm run lint
npm test
npm run build
```

| Report | Location |
|--------|----------|
| Backend coverage (JaCoCo) | `backend/target/site/jacoco/index.html` |
| Frontend coverage | `frontend/coverage/index.html` (`npm run test:coverage`) |

Every pull request is checked by [GitHub Actions](.github/workflows/ci.yml): backend build and tests with coverage,
frontend lint, format, test and build, and Docker image builds.

## Project structure

```
swiftdrop/
├── backend/                         Spring Boot application
│   ├── src/main/java/com/swiftdrop/
│   │   ├── common/                  Shared: security (JWT), error handling, messaging, logging, CORS
│   │   └── <module>/                identity, booking, parcel, dispatch, delivery, merchant, notification, ...
│   │       ├── controller/          REST endpoints
│   │       ├── service/             Business logic, the module's public entry point
│   │       ├── repository/          Data access (private to the module)
│   │       ├── model/               JPA entities and enums
│   │       ├── dto/                 Request and response records
│   │       ├── mapper/              Entity ↔ DTO mapping
│   │       ├── exception/           Module-specific exceptions
│   │       └── event/               Message publishers and listeners
│   └── src/main/resources/
│       ├── application*.yml         Configuration profiles
│       └── db/migration/            Flyway migrations
├── frontend/                        React single-page application
│   └── src/
│       ├── api/                     Axios client and API calls
│       ├── components/              Shared UI kit and state components
│       ├── features/                One folder per feature: its routes.ts and pages (see features/README.md)
│       ├── shared/                  Layouts, route guards and error pages
│       ├── routes/                  Router, paths for every screen, feature registry
│       └── context/  hooks/  types/  utils/
├── docs/                            Module guides, API conventions, data model, Kubernetes
├── docker/                          Additional Docker configuration
├── infra/                           Helm chart (helm/swiftdrop) and Kubernetes deploy script (k8s/)
├── .github/                         CI workflow and PR template
├── docker-compose.yml               Full stack
├── docker-compose.dev.yml           Hot-reload override
└── .env.example                     Environment template
```

## API at a glance

- Base path: **`/api/v1`**. Interactive documentation at `/swagger-ui.html` (dev profile).
- Authentication: `Authorization: Bearer <JWT>`. Roles: `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER_ADMIN`.
- Public endpoints: `/api/v1/auth/**`, `/api/v1/public/tracking/**`, `/api/v1/health`.
- Merchant portal, API keys and the `ApiKeyApi` contract: [docs/merchant-portal.md](docs/merchant-portal.md).
- Errors share one format:

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

Full conventions: [docs/API_CONVENTIONS.md](docs/API_CONVENTIONS.md).

### Authentication

Email and password sign-in with short-lived JWTs, plus Sign in with Google for customers (`POST /api/v1/auth/google`).

| Endpoint | Access | Purpose |
|----------|--------|---------|
| `POST /api/v1/auth/register/customer` | Public | Create a customer account and sign in (201 + token) |
| `POST /api/v1/auth/register/merchant` | Public | Submit a business for approval (201, no token until approved) |
| `POST /api/v1/auth/login` | Public | Sign in. 401 for wrong credentials, 403 if pending approval, disabled, or on another role's sign-in page (`portal`: `PUBLIC` default, `COURIER`, `ADMIN`) |
| `POST /api/v1/auth/refresh` | Public (refresh token) | New access + refresh token pair; the web app calls it automatically |
| `POST /api/v1/auth/password-reset` | Public | Email a reset link (always 202, so emails can't be discovered) |
| `POST /api/v1/auth/password-reset/confirm` | Public | Set a new password with the emailed token (single use, 30 min) |
| `GET /api/v1/users/me` | Any signed-in user | Own profile |
| `GET /api/v1/admin/users?search=&role=&status=&page=0&size=20` | `DISPATCHER_ADMIN` | Accounts, newest first, filtered by name/email/phone, role and status |
| `GET /api/v1/admin/users/{id}` | `DISPATCHER_ADMIN` | One account |
| `PATCH /api/v1/admin/users/{id}/role` | `DISPATCHER_ADMIN` | Change between `CUSTOMER` and `COURIER` (`{"role":"COURIER"}`); ends the user's sessions |
| `PATCH /api/v1/admin/users/{id}/status` | `DISPATCHER_ADMIN` | Activate, suspend or deactivate (`{"status":"SUSPENDED"}`); suspending or deactivating ends the user's sessions |
| `GET /api/v1/admin/merchants?status=PENDING` | `DISPATCHER_ADMIN` | Merchants awaiting review |
| `POST /api/v1/admin/merchants/{id}/approve` · `/reject` | `DISPATCHER_ADMIN` | Approve (activates the merchant's sign-in) or reject with a reason |
| `GET` · `PUT /api/v1/admin/settings` | `DISPATCHER_ADMIN` | System settings; saving needs the `version` it was loaded with (409 if someone saved in between) |
| `GET /api/v1/public/settings` | Public | Contact details, date/time formats, parcel limits and whether maintenance mode is on |

**User management rules:** there is one admin account (set in `.env`), so the admin role can't be given or taken away and the admin always stays active. Merchant accounts belong to a registered business: they come from merchant registration and approval, not a role change, and a pending or rejected merchant is handled through the merchant review. Other modules use the `UserApi` interface (`user/api`), not `UserService` or the repository, and can listen for `UserRoleChangedEvent` / `UserAccountStatusChangedEvent`.

**Maintenance mode** (System Settings): while it's on, every API call from anyone but the admin gets `503 MAINTENANCE_MODE`, only the admin portal accepts sign-ins, and the web app shows a maintenance page. The admin keeps full access to switch it off.

Sessions: sign-in returns a 15-minute **access token** (`JWT_EXPIRY`) and a **refresh token**. The web app renews the access
token in the background, so users stay signed in while the refresh token is valid: **30 days with
"Remember me"** (renewed on every use, kept after the browser closes) or **at most 1 day without it** (forgotten
when the browser closes). Changing the password ends all existing sessions.

Passwords are stored as BCrypt hashes. There is exactly one admin (enforced by a unique index), and its
credentials are always `ADMIN_EMAIL` / `ADMIN_PASSWORD`: no self-registration and no password reset by email. Admins create couriers (`/api/v1/admin/couriers`).

Each role has its own sign-in page, and the backend refuses a sign-in on the wrong one (`WRONG_SIGN_IN_PAGE`,
only after the correct password): customers and merchants at `/login`, couriers at `/courier/login`, admins at
`/admin/login`. Google sign-in is only on the main page, so staff always use their password.

## Documentation

| Topic | Guide |
|-------|-------|
| Data model and migrations | [docs/DATA_MODEL.md](docs/DATA_MODEL.md) |
| API conventions and errors | [docs/API_CONVENTIONS.md](docs/API_CONVENTIONS.md) |
| Module ownership | [docs/MODULE_OWNERSHIP.md](docs/MODULE_OWNERSHIP.md) |
| Parcels and status flow | [docs/parcel.md](docs/parcel.md) |
| Dispatch and assignment | [docs/dispatch.md](docs/dispatch.md) |
| Courier app | [docs/courier-app.md](docs/courier-app.md) |
| Public and live tracking | [docs/tracking.md](docs/tracking.md), [docs/realtime.md](docs/realtime.md) |
| Merchant portal, API keys, webhooks | [docs/merchant-portal.md](docs/merchant-portal.md) |
| Service areas and settings | [docs/config.md](docs/config.md) |
| AI assistant | [docs/assistant.md](docs/assistant.md), [docs/assistant-eval.md](docs/assistant-eval.md) |
| Kubernetes deployment | [docs/kubernetes.md](docs/kubernetes.md) |

## Contributing

1. Branch from `development`: `feature/<short-name>`, `fix/<short-name>` or `chore/<short-name>`.
2. Write [Conventional Commits](https://www.conventionalcommits.org/), e.g. `feat(parcel): add fee estimate endpoint`.
3. Add database changes as a **new** Flyway migration; never edit a merged one.
4. Open a pull request into `development` using the template; CI must pass and at least one review is required.
   `development` is merged into `main` for releases.

Read the full guide in [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) before your first pull request.

## Acknowledgements

Developed by **Group 32 · Code_Crafters** for *IN3101 Enterprise Application Development*,
University of Moratuwa.

## License

Released under the [MIT License](LICENSE).
