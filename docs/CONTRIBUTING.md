# Contributing to SwiftDrop

Group 32 "Code_Crafters". Please read this once before your first PR.

## 1. Branches

- `main` is always deployable. **No direct pushes**; everything goes through a PR.
- Branch from the latest `main`:

  | Type    | Pattern                       | Example                         |
  |---------|-------------------------------|---------------------------------|
  | Feature | `feature/m<N>-<short-name>`   | `feature/m3-parcel-booking`     |
  | Bug fix | `fix/m<N>-<short-name>`       | `fix/m6-duplicate-emails`       |
  | Chores  | `chore/<short-name>`          | `chore/upgrade-spring-boot`     |
  | Docs    | `docs/<short-name>`           | `docs/api-examples`             |

  `<N>` is your member number (see [MODULE_OWNERSHIP.md](MODULE_OWNERSHIP.md)). Use lowercase and hyphens.
- Keep branches short-lived (a few days). Rebase on or merge `main` often to avoid big conflicts.

## 2. Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <summary in imperative mood, max ~72 chars>

feat(parcel): add fee estimate endpoint
fix(dispatch): prevent assigning a parcel twice
test(identity): cover expired JWT
docs: explain migration naming
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`. Scope = module name (`parcel`, `frontend`, ...).
Small, focused commits are easier to review.

## 3. Pull requests

- Fill in the PR template. Keep PRs small (ideally < 400 changed lines).
- **At least 1 approval** from another member is required; **2 approvals** for changes to shared code:
  `common/`, `V1__init.sql`, event records, security config, `docker-compose.yml`, CI, frontend routes/layout.
- CI must be green (backend build + tests, frontend lint + test + build).
- The author merges after approval using **Squash and merge**, then deletes the branch.
- Reviewers: respond within 24 hours. Comment on behaviour, correctness, security and readability; be kind and specific.
- Don't merge your own PR without review, and don't approve code you haven't run or read.

## 4. Code conventions

### Backend
- Package by module: `com.swiftdrop.<module>.{controller,service,repository,model,dto.request,dto.response,mapper,exception}`
  (+ `event` for modules that publish/consume RabbitMQ messages). Each module's `package-info.java` describes its scope.
- Tests mirror the main packages: `src/test/java/com/swiftdrop/<module>/...`.
- Controllers stay thin: validate input, call a service, map to a DTO. Business logic lives in services.
- A module talks to another module through that module's **service** (or events), never its repository.
- Use constructor injection (`@RequiredArgsConstructor`), not field `@Autowired`.
- Throw the exceptions in `common.exception`; never return stack traces or internal messages.
- Write tests for services (unit) and controllers (`@WebMvcTest`). Integration tests end in `*IT.java`.

### Frontend
- Pages go in `src/pages/<role>/`, reusable UI in `src/components/`, API calls in `src/api/<module>.ts` using `apiClient`.
- Add routes in `src/routes/paths.ts` + `AppRoutes.tsx`; nav items in `NAV_ITEMS`.
- Run `npm run lint` and `npm run format` before pushing.

See [API_CONVENTIONS.md](API_CONVENTIONS.md) for API, DTO and database naming.

## 5. Database migrations (Flyway)

Flyway owns the schema. Hibernate runs with `ddl-auto: validate` and never changes tables.

- Location: `backend/src/main/resources/db/migration/`
- **Name: `V<member>_<3-digit sequence>__<description>.sql`**
  - Member 3's first migration: `V3_001__create_parcels.sql`, next: `V3_002__add_parcel_weight_index.sql`
  - Member 10: `V10_001__...sql`
  - This keeps versions unique across the team, so parallel PRs don't conflict.
- **Never edit a migration that has been merged to `main`**; add a new one. (Flyway checksums will fail otherwise.)
- If your table references another member's table (foreign key), that member's migration must already be on `main`
  **and** have a lower version, or you coordinate the order with them.
- The `dev` profile enables Flyway `out-of-order` so a lower version merged later still applies on dev databases.
- To reset your local DB: `docker compose down -v` (deletes the `postgres_data` volume) and start again.

## 6. Security and privacy rules

- **Never commit secrets.** `.env` is git-ignored; only `.env.example` (with placeholders) is committed.
  If you commit a secret by accident, tell the team: it must be rotated, not just deleted.
- **Never log personal data**: names, emails, phone numbers, addresses, passwords, tokens, API keys, request bodies.
  Log IDs instead: `log.info("Parcel {} assigned to courier {}", parcelId, courierId)`.
  The log masker (`PiiMaskingConverter`) is a safety net, not permission.
- Don't put personal data in URLs/query strings, error messages or RabbitMQ events.
- Hash passwords with the provided `PasswordEncoder` (BCrypt). Never store or compare plain text.
- Every endpoint that returns user data must check the caller owns it (not just their role).
