# MOLS — Quick Help

Short operational guide for local development and validation.

## Prerequisites

- Java 21+
- PostgreSQL running locally
- Database and credentials expected by default:
	- DB: logistics_db
	- User: logistics_user
	- Password: logistics123 (override via `SPRING_DATASOURCE_PASSWORD`)

Configuration lives in src/main/resources/application.properties. Secrets are
read from the environment — see `.env.example`. The database schema is managed
by Flyway migrations (`src/main/resources/db/migration`); Hibernate runs in
`validate` mode and never modifies the schema.

## Run Locally

The JWT signing key is required and has no committed default. Set it once
(generate with `openssl rand -hex 32`), then run:

```powershell
$env:SECURITY_JWT_SECRET_KEY = "your-generated-key"
./mvnw.cmd clean compile
./mvnw.cmd spring-boot:run
```

On first start against an empty database, Flyway creates the full schema (V1).
Against a database previously created by Hibernate `ddl-auto`, Flyway baselines
it and applies any newer migrations.

API and docs:

- API base: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

UI:

- UI entry: http://localhost:8080/ui
- Login page: http://localhost:8080/ui/login

## Authentication and Authorization (JWT)

Public endpoints:

- POST /api/auth/login

Admin-only:

- POST /api/auth/register (no public self-signup)

Protected endpoint policy (aligned with the UI role model):

- GET /api/** requires authenticated token (ADMIN / OPERATOR / AUDITOR)
- POST/PUT/PATCH on /api/orders/**, /api/order-items/**, /api/shipments/**
  requires ADMIN or OPERATOR (DELETE /api/order-items/** too — removing a
  line item is part of editing an order)
- Deleting whole orders/shipments and every other write requires ADMIN

JWT settings (read from the environment, see `.env.example`):

- `SECURITY_JWT_SECRET_KEY` (required, no committed default)
- `SECURITY_JWT_EXPIRATION_MS` (optional, default 24h)

## UI Login (Session)

The `/ui/**` area uses form login + session authentication.

Roles:

- ADMIN: full access + user management
- OPERATOR: can create/edit Orders and Shipments (no deletes, no master data changes)
- AUDITOR: read-only

Admin UI:

- Users management: http://localhost:8080/ui/users (ADMIN only)

Application users are stored in PostgreSQL table `app_users` (not PostgreSQL roles).

Bootstrap admin (dev-only) can be configured via environment variables:

- `BOOTSTRAP_ADMIN_ENABLED`
- `BOOTSTRAP_ADMIN_USERNAME`
- `BOOTSTRAP_ADMIN_PASSWORD`

If you don't know an application user's password, reset it in DB (requires `pgcrypto`):

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE app_users
SET password = crypt('NEW_PASSWORD', gen_salt('bf', 10))
WHERE username = 'admin';
```

## Run Tests

Full suite:

```powershell
./mvnw.cmd test
```

Current verified local status: 124 tests passing.

## CI Pipeline

GitHub Actions workflow:

- .github/workflows/ci.yml

Triggers and checks:

- Runs on push and pull_request to main
- Executes:
	- ./mvnw clean compile -B
	- ./mvnw test -B

## Useful References

- README.md
- PROJECT_OVERVIEW.md

