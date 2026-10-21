# docker/

Reserved for extra Docker configuration that doesn't belong to a single service, for example:

- `postgres/init/*.sql`: one-off init scripts for the Postgres container (schema itself is managed by Flyway, not here)
- `rabbitmq/definitions.json`: broker definitions, if we ever need them outside the app
- a local SMTP catcher (e.g. Mailpit) compose override for testing emails

Service images are built from `backend/Dockerfile` and `frontend/Dockerfile`.
