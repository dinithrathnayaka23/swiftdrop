## What

<!-- One or two sentences: what does this PR change? -->

## Why

<!-- Link the task/issue, e.g. "Closes #12" -->

## How to test

<!-- Steps a reviewer can follow -->

## Checklist

- [ ] Branch name follows `feature/m<N>-<short-name>` (or `fix/`, `chore/`, `docs/`)
- [ ] Backend: `./mvnw verify` passes locally
- [ ] Frontend: `npm run lint && npm test && npm run build` pass locally
- [ ] New DB changes are in a **new** Flyway migration using my member prefix (`V<N>_<seq>__...sql`)
- [ ] No secrets, `.env` files or personal data committed; nothing personal is logged
- [ ] API follows [docs/API_CONVENTIONS.md](../docs/API_CONVENTIONS.md) (paths, DTOs, error format)
- [ ] Changes to shared code (`common`, events, `V1__init.sql`, routes) were discussed with affected members
