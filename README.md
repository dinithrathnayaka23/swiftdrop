# SwiftDrop

Parcel Delivery & Courier Management System.
University of Moratuwa · IN3101 Enterprise Application Development · Group 32 "Code_Crafters".

Customers book parcel deliveries and track them, merchants push orders through an API,
dispatchers assign couriers, couriers update status with proof of delivery, and everyone
gets notified as parcels move.

> **Status:** project skeleton. Infrastructure, security baseline and UI shell are in place;
> feature modules are empty placeholders waiting for their owners.

## Architecture

A **layered modular monolith**: one Spring Boot application split into feature modules,
plus a separate React single-page app.

```
 Browser ──► frontend (Nginx: React SPA) ──/api──► backend (Spring Boot, /api/v1)
                                                    │            │
                                                    ▼            ▼
                                             PostgreSQL     RabbitMQ
                                             (Flyway)       (parcel events → notification, webhooks)
```

| Layer          | Tech                                                                                 |
|----------------|--------------------------------------------------------------------------------------|
| Backend        | Java 21, Spring Boot 3.5, Maven, Spring Security (JWT + Google OAuth), JPA, Flyway, AMQP, WebSocket, Mail, springdoc-openapi |
| Frontend       | React 18, Vite 6, TypeScript, Tailwind CSS 3, React Router 6, Axios                  |
| Data / messaging | PostgreSQL 16, RabbitMQ 3 (management)                                             |
| Tests          | JUnit 5, Spring Boot Test, Testcontainers, JaCoCo · Vitest, React Testing Library    |
| Runtime        | Docker Compose; GitHub Actions CI                                                    |

Each backend module (`identity`, `user`, `parcel`, `dispatch`, `merchant`, `notification`, `config`, `reporting`)
is a package under `com.swiftdrop` with `controller → service → repository` layers plus `model` and `dto`.
Shared building blocks live in `com.swiftdrop.common` (error handling, security, messaging, logging, CORS).
Modules talk to each other through services or RabbitMQ events, never through each other's repositories.

## Prerequisites

| Tool | Version | Needed for |
|------|---------|-----------|
| Docker Desktop (with Compose v2) | recent | everything (DB, broker, full stack, integration tests) |
| JDK | 21 (e.g. Temurin) | running/developing the backend locally |
| Maven | 3.9+ (optional, `./mvnw` is included) | backend build |
| Node.js | 22 LTS (20.19+ works) | frontend development |

No local PostgreSQL or RabbitMQ install is needed; both run in Docker.

## Quick start (everything in Docker)

```bash
cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
docker compose up --build
```

| What | URL |
|------|-----|
| Frontend | http://localhost:3000 |
| Backend ping | http://localhost:8080/api/v1/health |
| Actuator health | http://localhost:8080/actuator/health |
| Swagger UI (dev profile) | http://localhost:8080/swagger-ui.html |
| RabbitMQ management | http://localhost:15672 (user/password from `.env`) |
| PostgreSQL | `localhost:5432` (credentials from `.env`) |

The login page has a temporary **"Dev only: sign in as a role"** panel so you can click through
each role's screens before real authentication exists (`VITE_ENABLE_DEV_LOGIN`).

Stop with `docker compose down`. Add `-v` to also wipe the database and broker volumes.

## Local development (backend/frontend on your machine)

Run only the infrastructure in Docker:

```bash
cp .env.example .env                    # once
docker compose up -d postgres rabbitmq
```

**Backend** (port 8080). The `dev` profile reads the repo-root `.env` automatically:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # Windows: mvnw.cmd ...
```

In IntelliJ: run `SwiftDropApplication` with active profile `dev` and working directory `backend/`.

**Frontend** (port 5173, proxies `/api` to `localhost:8080`):

```bash
cd frontend
npm install
npm run dev
```

**Hot reload inside Docker instead?**
`docker compose -f docker-compose.yml -f docker-compose.dev.yml up` runs Vite with HMR on :5173 and the
backend from source (restart the `backend` container to pick up Java changes).

## Tests and coverage

```bash
# Backend: unit tests + Testcontainers integration tests (*IT, needs Docker) + JaCoCo
cd backend
./mvnw verify
# Coverage report: backend/target/site/jacoco/index.html (XML: jacoco.xml)
./mvnw test            # unit tests only (no Docker needed)

# Frontend
cd frontend
npm run lint
npm test               # Vitest
npm run test:coverage  # coverage/index.html
npm run build
```

CI ([.github/workflows/ci.yml](.github/workflows/ci.yml)) runs all of the above on every PR and push to `main`.

## Ports

| Service | Port(s) |
|---------|---------|
| frontend (Nginx) | 3000 (`FRONTEND_PORT`) |
| frontend (Vite dev) | 5173 |
| backend | 8080 (`BACKEND_PORT`) |
| postgres | 5432 |
| rabbitmq | 5672 (AMQP), 15672 (management UI) |

## Configuration

All configuration comes from environment variables; see [.env.example](.env.example).
Spring profiles: `application.yml` (base), `application-dev.yml` (Swagger on, reads `.env`, Flyway out-of-order),
`application-test.yml` (used by tests). There are no secrets in the repository: `.env` is git-ignored.

## Project structure

```
swiftdrop/
├── backend/                     Spring Boot app
│   ├── src/main/java/com/swiftdrop/
│   │   ├── common/              shared: api paths, exceptions, security (JWT), messaging, logging, CORS
│   │   ├── identity/ user/ parcel/ dispatch/ merchant/ notification/ config/ reporting/
│   │   │   └── controller/ service/ repository/ model/ dto/
│   │   └── SwiftDropApplication.java
│   ├── src/main/resources/
│   │   ├── application*.yml
│   │   └── db/migration/        Flyway migrations
│   ├── src/test/java/...        unit, @WebMvcTest and Testcontainers (*IT) tests
│   └── Dockerfile
├── frontend/                    React SPA
│   ├── src/{api,components,layouts,pages,routes,hooks,context,types,utils}
│   │   └── pages/{auth,customer,merchant,courier,dispatcher,admin,public}
│   ├── nginx.conf
│   └── Dockerfile
├── docker/                      extra Docker configs (reserved)
├── docs/                        CONTRIBUTING, API conventions, module ownership
├── .github/                     CI workflow, PR template
├── docker-compose.yml           full stack
├── docker-compose.dev.yml       hot-reload override
└── .env.example
```

## Database migrations

Flyway owns the schema (`spring.jpa.hibernate.ddl-auto=validate`). `V1__init.sql` creates only the baseline
(`users`, `roles`, `user_roles`).

**Each member adds their own tables in separate versioned migrations, prefixed with their member number**,
so parallel work never clashes:

```
V1__init.sql                 baseline (shared)
V3_001__create_parcels.sql   Member 3
V3_002__add_parcel_indexes.sql
V5_001__create_merchant_api_keys.sql  Member 5
```

Never edit a merged migration. Full rules are in [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md#5-database-migrations-flyway).

## Module ownership

The scope has **not been divided yet**. When it is, fill in this table and
[docs/MODULE_OWNERSHIP.md](docs/MODULE_OWNERSHIP.md).

| Member | Module / area | Branch prefix | Migration prefix |
|--------|---------------|---------------|------------------|
| 1  | TBD | `feature/m1-*`  | `V1_xxx__` |
| 2  | TBD | `feature/m2-*`  | `V2_xxx__` |
| 3  | TBD | `feature/m3-*`  | `V3_xxx__` |
| 4  | TBD | `feature/m4-*`  | `V4_xxx__` |
| 5  | TBD | `feature/m5-*`  | `V5_xxx__` |
| 6  | TBD | `feature/m6-*`  | `V6_xxx__` |
| 7  | TBD | `feature/m7-*`  | `V7_xxx__` |
| 8  | TBD | `feature/m8-*`  | `V8_xxx__` |
| 9  | TBD | `feature/m9-*`  | `V9_xxx__` |
| 10 | TBD | `feature/m10-*` | `V10_xxx__` |

Modules to assign: identity, user, parcel, dispatch, merchant, notification, config, reporting,
plus frontend shell and DevOps/QA.

## Branching and pull requests

- `main` is protected: work on `feature/m<N>-<short-name>` branches, open a PR, get **1 approval**
  (**2** for shared code such as `common/`, events, security, compose, CI), and keep CI green.
- Commit messages follow Conventional Commits: `feat(parcel): add fee estimate endpoint`.
- Squash and merge, then delete the branch.

Details: [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) · API rules: [docs/API_CONVENTIONS.md](docs/API_CONVENTIONS.md)

## License

[MIT](LICENSE)
