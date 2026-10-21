<div align="center">

# SwiftDrop

**Parcel delivery and courier management, from booking to doorstep.**

[![CI](https://github.com/dinithrathnayaka23/swiftdrop/actions/workflows/ci.yml/badge.svg)](https://github.com/dinithrathnayaka23/swiftdrop/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-FF6600?logo=rabbitmq&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Overview](#overview) •
[Architecture](#architecture) •
[Getting started](#getting-started) •
[Development](#local-development) •
[Testing](#testing) •
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

Status changes flow through an event bus, so notifications (email, SMS, live updates) and merchant webhooks
happen asynchronously without slowing down the core workflow.

> [!NOTE]
> The project is in its **foundation stage**: infrastructure, security baseline, module structure and the UI shell
> are in place, and features are being built module by module.

## Architecture

SwiftDrop is a **layered modular monolith**: a single Spring Boot application divided into well-bounded
feature modules, with a separate React single-page application in front of it.

```mermaid
flowchart LR
    user([Browser]) --> fe["<b>frontend</b><br/>React SPA · Nginx"]
    fe -- "/api/v1" --> be

    subgraph be["<b>backend</b> · Spring Boot"]
        direction TB
        mods["identity · user · parcel · dispatch<br/>merchant · notification · config · reporting"]
        common["common: security · errors · messaging · logging"]
        mods --- common
    end

    be <--> db[("PostgreSQL<br/>schema via Flyway")]
    be <-- "parcel events" --> mq[["RabbitMQ"]]
```

**Design principles**

- **Modules own their data.** Each module exposes services (and events); other modules never reach into its repositories or entities.
- **Thin controllers, rich services.** Controllers validate and map DTOs; business rules live in services.
- **Stateless and secure by default.** JWT authentication, role-based method security, and every route private unless explicitly public.
- **Asynchronous side effects.** Parcel status changes are published to RabbitMQ; notifications and webhooks consume them, with a dead-letter queue for failures.
- **Schema as code.** Flyway migrations are the single source of truth; Hibernate only validates.
- **Privacy first.** No stack traces or personal data in API errors, and personal data is never logged.

### Backend modules

| Module | Responsibility |
|--------|----------------|
| `identity` | Sign-up, login, JWT issuing, Google OAuth |
| `user` | Profiles, roles, merchant approval |
| `parcel` | Booking, fee estimation, parcel lifecycle, public tracking |
| `dispatch` | Courier assignment, status updates, proof of delivery and failures |
| `merchant` | API keys, merchant order API, webhooks |
| `notification` | Event consumers, email / SMS, live updates |
| `config` | Delivery zones and pricing rules |
| `reporting` | Operational and business reports |

## Tech stack

| Area | Technologies |
|------|--------------|
| **Backend** | Java 21, Spring Boot 3.5 (Web, Validation, Data JPA, Security, OAuth2 Client, AMQP, WebSocket, Mail, Actuator), springdoc-openapi, Lombok |
| **Frontend** | React 18, TypeScript, Vite 6, Tailwind CSS 3, React Router 6, Axios |
| **Data & messaging** | PostgreSQL 16, Flyway, RabbitMQ 3 |
| **Testing** | JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers, JaCoCo, Vitest, React Testing Library |
| **Tooling** | Maven Wrapper, ESLint, Prettier, Docker Compose, GitHub Actions |

## Getting started

### Prerequisites

| Tool | Version | Required for |
|------|---------|--------------|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Compose v2+ | Running the stack and integration tests |
| [JDK](https://adoptium.net/) | 21 | Backend development |
| [Node.js](https://nodejs.org/) | 22 LTS (20.19+) | Frontend development |

Maven is optional: the repository ships with the Maven Wrapper (`./mvnw`).
PostgreSQL and RabbitMQ run in Docker; nothing else needs to be installed.

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
| PostgreSQL | `localhost:5432` |

Credentials for PostgreSQL and RabbitMQ are the ones in your `.env`.

> [!TIP]
> Until authentication is implemented, the login page offers a **dev-only role switcher** to explore each
> role's screens. It is controlled by `VITE_ENABLE_DEV_LOGIN` and must be disabled for any real deployment.

Stop everything with `docker compose down` (add `-v` to also delete the database and broker data).

## Local development

For day-to-day work, run the infrastructure in Docker and the applications on your machine:

```bash
docker compose up -d postgres rabbitmq
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
| `JWT_SECRET`, `JWT_EXPIRY` | Token signing key (≥ 32 chars) and lifetime in seconds |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google sign-in |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Outgoing email |
| `FRONTEND_URL` | Allowed CORS origin(s), comma-separated |
| `VITE_ENABLE_DEV_LOGIN` | Shows the dev role switcher in Docker builds |

> [!IMPORTANT]
> The values in `.env.example` are for local development only. Generate a strong `JWT_SECRET`
> (`openssl rand -base64 48`) and unique passwords for any shared or hosted environment.

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
│   │   └── <module>/                identity, user, parcel, dispatch, merchant, notification, config, reporting
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
│       ├── components/  layouts/    Shared UI and page shells
│       ├── pages/                   auth, customer, merchant, courier, dispatcher, admin, public
│       ├── routes/                  Routing and role-based guards
│       └── context/  hooks/  types/  utils/
├── docs/                            Contributing guide and API conventions
├── docker/                          Additional Docker configuration
├── .github/                         CI workflow and PR template
├── docker-compose.yml               Full stack
├── docker-compose.dev.yml           Hot-reload override
└── .env.example                     Environment template
```

## API at a glance

- Base path: **`/api/v1`**. Interactive documentation at `/swagger-ui.html` (dev profile).
- Authentication: `Authorization: Bearer <JWT>`. Roles: `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER_ADMIN`.
- Public endpoints: `/api/v1/auth/**`, `/api/v1/public/tracking/**`, `/api/v1/health`.
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

## Contributing

1. Branch from `main`: `feature/<short-name>`, `fix/<short-name>` or `chore/<short-name>`.
2. Write [Conventional Commits](https://www.conventionalcommits.org/), e.g. `feat(parcel): add fee estimate endpoint`.
3. Add database changes as a **new** Flyway migration; never edit a merged one.
4. Open a pull request using the template; CI must pass and at least one review is required.

Read the full guide in [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) before your first pull request.

## Acknowledgements

Developed by **Group 32 · Code_Crafters** for *IN3101 Enterprise Application Development*,
University of Moratuwa.

## License

Released under the [MIT License](LICENSE).
