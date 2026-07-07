# MOLS — Project Overview

## Purpose

MOLS is a Spring Boot logistics app with:

- a REST API (JWT-secured) for CRUD operations
- a server-rendered admin UI (`/ui`) for day-to-day usage
- stock + movement auditing so changes are traceable — the movement log is
  **append-only** (never updated or deleted) and records **who** made each change

The goal of this doc is to help a developer understand where things live, how the flow works, and which business rules are enforced in the services.

## Project Status

- Runtime: Spring Boot 4.x on Java 21
- Database: PostgreSQL (local or via Docker Compose)
- API: `/api/**` (Swagger at `/swagger-ui.html`)
- UI: `/ui/**` (Thymeleaf + Bootstrap, dark mode) — being migrated to a
  React SPA served at `/app/**` (see `frontend/` and
  `docs/UI_MIGRATION_PLANNING.md`)
- Build/tests: Maven wrapper (`./mvnw.cmd verify`); the Maven build also
  lints, tests and builds the frontend (skippable with `-Dskip.frontend=true`)
- CI: GitHub Actions workflow in `.github/workflows/ci.yml`

## Repo Layout (High Level)

### REST API

Controllers live in `src/main/java/com/mls/logistics/controller` and expose endpoints like:
- `WarehouseController` — `/api/warehouses`
- `UnitController` — `/api/units`
- `ResourceController` — `/api/resources`
- `StockController` — `/api/stocks`
- `OrderController` — `/api/orders`
- `OrderItemController` — `/api/order-items`
- `VehicleController` — `/api/vehicles`
- `ShipmentController` — `/api/shipments`
- `MovementController` — `/api/movements`

### Services

Most business rules are in `src/main/java/com/mls/logistics/service`. Controllers should stay thin.

**Service Classes**:
- `WarehouseService`, `UnitService`, `ResourceService`, `StockService`
- `OrderService`, `OrderItemService`, `VehicleService`, `ShipmentService`, `MovementService`

**Note**: `@Transactional` boundaries are implemented with read-only defaults.

### Persistence

Repositories live in `src/main/java/com/mls/logistics/repository` (Spring Data JPA).

### Domain

Entities live in `src/main/java/com/mls/logistics/domain`:
- `Unit`, `Warehouse`, `Resource`, `Stock`
- `Order`, `OrderItem`, `Vehicle`, `Shipment`, `Movement`

DTOs live in `src/main/java/com/mls/logistics/dto` (request/response).

Exceptions live in `src/main/java/com/mls/logistics/exception` (global handler + typed exceptions).

Security lives in `src/main/java/com/mls/logistics/security` (JWT for API + form login for UI).

### Frontend: React SPA (incremental migration)

The new interface lives in `frontend/` (Vite + React 19 + TypeScript +
Tailwind 4) and is served at `/app/**`:

- **Auth**: cookie-based (the HttpOnly JWT cookie from `POST /api/auth/login`);
  session restored on page load via `GET /api/auth/me`; a 401 from any API
  call drops the client session and returns the user to the login page.
- **Shell**: sidebar/topbar layout, role-aware navigation (ADMIN-only entries
  hidden and route-guarded client-side; real enforcement is the API role
  matrix), light/dark theme, military-green design tokens in `src/index.css`.
- **Pages**: Sprint 1 ships the KPI dashboard (fed by `GET /api/dashboard`);
  other sections are placeholders linking to their working `/ui` pages.
- **Serving**: the production build is packaged into the jar at
  `static/app/` and served by `config/SpaWebConfig` with an `index.html`
  fallback for client-side routes.
- **Dev loop**: `npm run dev` inside `frontend/` (Vite proxies `/api` to
  `localhost:8080`); `npm run lint` (oxlint) and `npm test` (Vitest) run in
  the Maven build too.

### UI: Server-Side Admin Interface

The project includes a server-rendered admin UI built with Thymeleaf.

- **UI Controller**: `web/UiController` (Spring MVC `@Controller`)
- **Templates**: `src/main/resources/templates/ui/*` + fragments under `templates/fragments/*`
- **Static assets**: `src/main/resources/static/*`
- **Theme**: Bootstrap 5.3 color modes (dark mode toggle)

**UI Routes**:
- Dashboard: `/ui`
- Login: `/ui/login`
- First-run setup: `/ui/setup` (only when no users exist)
- Logout (POST): `/ui/logout`
- Warehouses: `/ui/warehouses`
- Resources: `/ui/resources`
- Vehicles: `/ui/vehicles`
- Stock: `/ui/stocks` (create + adjust + delete)
- Audit log: `/ui/movements`
- Orders: `/ui/orders` (expand items in-table; create/edit with inline items)
- Order detail: `/ui/orders/{id}` (shipments + linked movements)
- Shipments: `/ui/shipments`
- Shipment detail: `/ui/shipments/{id}` (order context + linked movements)
- Units: `/ui/units`
- Users (ADMIN-only): `/ui/users` (create users, change roles, reset passwords, enable/disable)

#### Dashboard (Operational)

The `/ui` dashboard is a quick operational snapshot. It shows:

- KPI cards with context (pending orders badge, fulfillment target flag, etc.)
- Charts (Chart.js v4) with safe fallbacks when the database has little/no historical data
- Recent activity (last 15 movements)
- Actionable alerts:
   - Low stock items with direct link to the stock adjust screen
   - Stale pending orders with direct link to the order detail page

**Note about statuses**: statuses are typed enums persisted as strings
(`@Enumerated(STRING)`) and constrained with database CHECKs. On the dashboard, “pending” maps to `CREATED + VALIDATED`.

Dashboard thresholds are configurable via `mols.dashboard.*` in `application.properties`:

- `mols.dashboard.low-stock-threshold`
- `mols.dashboard.critical-stock-threshold`
- `mols.dashboard.low-stock-list-limit`
- `mols.dashboard.stale-order-days`
- `mols.dashboard.stale-orders-list-limit`
- `mols.dashboard.recent-activity-hours`
- `mols.dashboard.movement-chart-days`
- `mols.dashboard.fulfillment-target-percent`

Implementation notes:

- Aggregations live in services (the UI controller doesn’t talk to repositories).
- Recent movements query uses an `@EntityGraph` to avoid N+1 during view rendering.

**Security note**: `/api/**` remains JWT-protected. The UI uses form login + session auth; most `/ui/**` routes require login, with `/ui/login` and `/ui/setup` public.

### Configuration
- **Database**: PostgreSQL configured in `src/main/resources/application.properties`
- **Credentials/secrets**: Read from the environment. The JWT signing key
  (`SECURITY_JWT_SECRET_KEY`) is **required and has no committed default**; DB
  connection values have local-dev defaults that are overridable via env. See
  `.env.example`.
- **Schema management**: **Flyway** owns the schema (`src/main/resources/db/migration`).
  Hibernate runs with `ddl-auto=validate` and never modifies the schema; a
  mismatch between the entity model and the migrated schema fails fast at startup.
  - `V1` — baseline schema
  - `V2` — integrity hardening: status/quantity CHECKs, NOT NULLs,
    `UNIQUE (resource, warehouse)` on stocks, FK + audit-timeline indexes,
    `stocks.version` (optimistic locking) and `movements.created_by` (audit actor)
  - `V3` — optional `latitude`/`longitude` on warehouses and units (range
    CHECKs), groundwork for the logistics map in the React frontend
- **SQL Logging**: off by default (`SPRING_JPA_SHOW_SQL=true` to enable locally)
- **Port**: Application runs on `8080`
- **OpenAPI/Swagger**:
   - UI: `http://localhost:8080/swagger-ui.html`
   - JSON spec: `http://localhost:8080/v3/api-docs`
   - Springdoc properties configured in `application.properties`
   - Master switch `SPRINGDOC_ENABLED` (default `true` for local dev;
     docker-compose sets it to `false` for a production-like posture)
- **Security/JWT**:
   - Public auth endpoints: `POST /api/auth/login`, `POST /api/auth/logout`
     (logout only clears the auth cookie)
   - Admin-only: `POST /api/auth/register` (no public self-signup)
   - Browser clients (the SPA): login also sets the JWT in an
     `HttpOnly; SameSite=Strict; Path=/api` cookie, and `JwtAuthFilter`
     accepts the token from the Authorization header or that cookie. The
     token never needs to touch script-accessible storage (XSS hardening);
     `SameSite=Strict` is the CSRF mitigation for the stateless API.
    - JWT configuration is provided via environment variables (see `.env.example`):
       - `SECURITY_JWT_SECRET_KEY` (required, no committed default)
       - `SECURITY_JWT_EXPIRATION_MS` (optional)
       - `SECURITY_JWT_COOKIE_SECURE` (optional; set `true` behind HTTPS so
         the auth cookie is `Secure`)
    - Authorization model (aligned with the UI role model):
       - `GET /api/**` requires authenticated token (ADMIN / OPERATOR / AUDITOR)
       - `POST/PUT/PATCH` on `/api/orders/**`, `/api/order-items/**`,
         `/api/shipments/**` requires `ADMIN` or `OPERATOR`; `DELETE
         /api/order-items/**` too (removing a line item is part of editing an
         order, mirroring the UI's inline item removal)
       - Deleting whole orders/shipments and every other write requires `ADMIN`
       - Anything not explicitly matched is denied (`denyAll` fallback)
- **Brute-force protection**: after `SECURITY_LOCKOUT_MAX_ATTEMPTS` (default 5)
  consecutive failed logins a username is locked for
  `SECURITY_LOCKOUT_DURATION_MS` (default 15 min). Applies to both the API
  login and the UI form login; all auth events are logged (`SECURITY:` lines).
- **Password policy**: minimum 12 characters (API register, admin user
  management, and first-run setup).
- **UI Security**:
   - `/ui/**` uses Spring Security form login + server-side session
   - CSRF protection enabled for UI forms
   - Template conditional rendering uses Thymeleaf Spring Security dialect (`thymeleaf-extras-springsecurity6`)
- **Bootstrap admin** (dev): can create an initial ADMIN user if none exists (see `application.properties`).

### Operational Notes (Local Dev)

#### First-run UI setup

If there are no application users in the database, `/ui/login` redirects to `/ui/setup`.
This page allows creating the first `ADMIN` account.

#### UI users vs PostgreSQL roles

The UI login uses **application users** stored in the `app_users` table.
These are not the same as PostgreSQL roles/users you create in pgAdmin.

#### Bootstrap admin (dev)

The application can create an initial `ADMIN` user on startup **only if no `ADMIN` exists**.
Defaults can be overridden via environment variables:

- `BOOTSTRAP_ADMIN_ENABLED`
- `BOOTSTRAP_ADMIN_USERNAME`
- `BOOTSTRAP_ADMIN_PASSWORD`

Passwords are stored hashed (BCrypt).

#### User enable/disable safety

Application users can be enabled/disabled (e.g., via the admin UI). For safety when upgrading an existing database schema, the application includes a startup runner that prevents accidental lock-out by ensuring at least one enabled `ADMIN` exists.

#### Reset an application user password (PostgreSQL)

If you don't know the current password, reset it directly in the DB using `pgcrypto`:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE app_users
SET password = crypt('NEW_PASSWORD', gen_salt('bf', 10))
WHERE username = 'admin';
```
- **Docker**:
   - `Dockerfile` uses multi-stage build (`eclipse-temurin:21-jdk` → `eclipse-temurin:21-jre`)
   - `docker-compose.yml` orchestrates `mols-app` + `mols-db` with healthcheck
   - PostgreSQL host port mapping: `5433:5432`

## Where to Start (For a New Developer)

1. **Read Documentation**:
   - This file and `README.md`
   - `HELP.md` for local run / troubleshooting

2. **Understand the Domain Model**:
   - Inspect entities in `src/main/java/com/mls/logistics/domain`
   - All entities use constructor-based relationships

3. **Review the API**:
   - All controllers follow identical patterns
   - Check `WarehouseController` as the reference implementation
   - Explore endpoints in Swagger UI: `http://localhost:8080/swagger-ui.html`

4. **Setup Database**:
   ```powershell
   # Ensure PostgreSQL is running with logistics_db database
   # User: logistics_user, Password: logistics123
   ```

5. **Build and Run**:
   ```powershell
   ./mvnw.cmd clean install
   ./mvnw.cmd spring-boot:run
   ```

   **or with Docker Compose**:
   ```powershell
   docker compose up --build -d
   docker compose logs -f app
   ```

6. **Test the API**:
   ```bash
   # Get all warehouses
   curl http://localhost:8080/api/warehouses
   
   # Create a warehouse
   curl -X POST http://localhost:8080/api/warehouses \
     -H "Content-Type: application/json" \
     -d '{"name":"Central","location":"Madrid"}'
   ```

7. **Run Tests** (when available):
   ```powershell
   ./mvnw.cmd test
   ```

## Key Domain Concepts

- **`Unit`** — Organizational branch requesting resources (has location, name, optional coordinates)
- **`Warehouse`** — Physical storage location for resources (has location, name, optional coordinates)
- **`Resource`** — Item, part, or material (has type, criticality)
- **`Stock`** — Quantity of a resource in a warehouse (links Resource ↔ Warehouse)
- **`Order`** — Request placed by a Unit (has status, date)
- **`OrderItem`** — Individual line item in an order (links Order ↔ Resource, has quantity)
- **`Vehicle`** — Transport asset: land/air/sea (has type, capacity, status)
- **`Shipment`** — Assignment of resources to a vehicle (links Order ↔ Vehicle ↔ Warehouse)
- **`Movement`** — Audit record of stock changes (tracks type, quantity, datetime)

## Important Business Rules (Conceptual)

These rules are **enforced in services**, not controllers:

1. Stock must never go negative (also enforced by a DB CHECK and optimistic locking under concurrency)
2. Order items must not exceed available stock (validated against total availability)
3. Every stock change must generate a Movement record
4. Shipment delivery triggers fulfillment: transitioning a shipment to `DELIVERED` deducts stock (EXIT) per order items and records movements.
5. The movement audit trail is **append-only**: no API or UI path can update or
   delete a movement; corrections are made with a new compensating adjustment.
   Deleting stock with movement history, delivered shipments, or completed
   orders is rejected to keep the trail coherent. Each movement records the
   acting user (`created_by`) via JPA auditing.
6. Statuses follow explicit state machines (`OrderStatus`, `ShipmentStatus`):
   - Orders: `CREATED → VALIDATED → COMPLETED / CANCELLED` (terminal states are final; `CREATED → COMPLETED` is allowed because fulfillment is shipment-driven)
   - Shipments: `PLANNED ↔ IN_TRANSIT → DELIVERED` (DELIVERED is terminal)

### Current Enforcement Status

- ✅ Rule 1 implemented in `StockService.adjustStock()` and `StockService.createStock()`
   - Rejects negative initial quantity
   - Rejects adjustments that would produce negative stock (`InsufficientStockException`, HTTP 409)
- ✅ Rule 2 implemented in `OrderItemService` (create and update)
   - Rejects order items where requested quantity exceeds total available stock (`InsufficientStockException`, HTTP 409)
- ✅ Rule 3 implemented in `StockService.adjustStock()` and `StockService.createStock()`
   - Every stock increase/decrease records a `Movement` (`ENTRY`/`EXIT`) automatically
- ✅ Rule 4 implemented in `ShipmentService` on transition to `DELIVERED`
   - Deducts stock per order items and records `EXIT` movements
   - Prevents invalid transitions (e.g., reverting DELIVERED)

### Traceability Enhancements

Movements can optionally be linked to:

- an `orderId`
- a `shipmentId`
- a free-text `reason`

These fields are used by the UI detail pages to show end-to-end traceability.

## Developer Guidelines

This repo follows a simple convention:

- Controllers: HTTP only, call services
- Services: business rules + transactions
- Repos: persistence only

Code/comments are in English. Constructor injection is preferred throughout.

### Package Structure

```
src/main/java/com/mls/logistics/
├── controller/      # REST API controllers (9 files)
├── domain/          # JPA entities (9 entities)
├── repository/      # Spring Data repositories (9 interfaces)
├── service/         # Business logic services (9 services)
├── web/             # Thymeleaf UI controller + UI form/view models
├── security/        # JWT auth, users, roles, and security config
├── exception/       # Global exception handling and error contracts
├── dto/             # Request/response DTO contracts
├── config/          # OpenAPI and app configuration classes
└── LogisticsApplication.java

src/main/resources/
├── templates/        # Thymeleaf templates
│   ├── fragments/    # layout fragments
│   └── ui/           # UI pages
└── static/           # CSS/JS/assets
```

### Containerization

```
.
├── Dockerfile
├── docker-compose.yml
└── .dockerignore
```

### Continuous Integration (GitHub Actions)

- **`ci.yml`** — on every push/PR to `main`: `./mvnw verify -B` runs the full
  suite (unit + Testcontainers integration tests — no DB service container
  needed), enforces the JaCoCo line-coverage floor, and publishes the
  CycloneDX SBOM and coverage report as artifacts.
- **`codeql.yml`** — CodeQL static security analysis (push/PR + weekly).
- **`security-scan.yml`** — OWASP Dependency-Check against the NVD
  (weekly + manual dispatch, fails on CVSS ≥ 7). Supports an optional
  `NVD_API_KEY` secret for faster feed downloads.
- **`dependabot.yml`** — weekly dependency update PRs (Maven + Actions).
- CI optimization: Maven dependency caching (`~/.m2`)

### Test suite layout

- **Unit / slice tests** (`@WebMvcTest`, Mockito) — controllers and services
  in isolation.
- **Integration tests** (`src/test/java/.../integration`) — boot the full
  application against a disposable PostgreSQL (Testcontainers,
  `@ServiceConnection`, singleton container). They cover the end-to-end
  fulfillment flow, the role authorization matrix, login lockout, audit
  immutability, and concurrent stock adjustments (optimistic locking).

## Implementation Notes

This is an MVP that already covers the main CRUD flows + UI. The next obvious upgrades would be integration tests (Testcontainers) and a clearer fulfillment model if shipments need partial deliveries.

## API Endpoints Reference

All endpoints follow RESTful conventions:

| Entity | Base Path | GET All | GET by ID | POST Create | PUT Update | DELETE |
|--------|-----------|---------|-----------|-------------|------------|--------|
| Warehouse | `/api/warehouses` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Unit | `/api/units` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Resource | `/api/resources` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Stock | `/api/stocks` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Order | `/api/orders` | ✅ | ✅ | ✅ | ✅ | ✅ |
| OrderItem | `/api/order-items` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Vehicle | `/api/vehicles` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Shipment | `/api/shipments` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Movement | `/api/movements` | ✅ | ✅ | — | — | — |

**Movements are read-only** (append-only audit trail): they are generated by the
system on every stock change and cannot be created, edited, or deleted via the API.

**Pagination and filtering (opt-in)**: every list endpoint accepts optional
`page`, `size` (1–100, default 20) and `sort` (`field` or `field,desc`; fields
are whitelisted per entity) query parameters, plus entity-specific filters.
Without any of them the plain JSON array is returned (original contract). With
any of them the response becomes a stable envelope:

```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
```

Filters per endpoint (all optional, combinable with pagination):

| Endpoint | Filters |
|----------|---------|
| `/api/warehouses`, `/api/units` | `name` (case-insensitive fragment) |
| `/api/resources` | `name` (fragment), `type` (exact, case-insensitive) |
| `/api/vehicles` | `type` (exact, case-insensitive), `status` |
| `/api/stocks` | `warehouseId`, `resourceId` |
| `/api/orders` | `status`, `unitId` |
| `/api/order-items` | `orderId` |
| `/api/shipments` | `status`, `orderId` |
| `/api/movements` | `type`, `orderId`, `shipmentId` |

Paginated movements default to newest first (`dateTime,desc`). Invalid
pagination, sort or enum filter values return HTTP 400.

Additional stock operation:
- `PATCH /api/stocks/{id}/adjust` — adjusts stock by delta and auto-creates movement audit record.

Dashboard endpoint:
- `GET /api/dashboard` — aggregated operational snapshot (KPIs, chart series,
  low-stock/stale-order alerts, recent movements and the thresholds used),
  assembled by `DashboardService` and readable by any authenticated role. This
  is the API counterpart of the `/ui` dashboard for the React frontend.

Authentication endpoints:
- `POST /api/auth/register` — register user and return JWT
- `POST /api/auth/login` — authenticate and return JWT

## Troubleshooting

### Business Rule Validation (2026-02-17)
- Stock negative prevention validated: adjusting below zero returns HTTP 409.
- Automatic movement audit validated: stock changes create `ENTRY` or `EXIT` movements depending on the adjustment.
- Order item stock ceiling validated: excessive quantity returns HTTP 409.

### Security Validation (2026-02-17)
- `GET /api/warehouses` without token returns HTTP 403.
- `GET /api/warehouses` with valid token returns HTTP 200.
- `POST /api/warehouses` with `OPERATOR` token returns HTTP 403.

### Application Won't Start
- Check PostgreSQL is running: `psql -U logistics_user -d logistics_db`
- Verify credentials in `application.properties`
- Check logs for Hibernate schema creation errors

### Database Permission Errors
```sql
-- Grant schema privileges (as postgres user)
GRANT ALL ON SCHEMA public TO logistics_user;
ALTER DATABASE logistics_db OWNER TO logistics_user;
```

### Port Already in Use
- Change port in `application.properties`: `server.port=8081`
- Or kill process using port 8080

### Maven Build Fails
```powershell
./mvnw.cmd clean install -U  # Force update dependencies
```

### Docker Compose Issues
- Check containers:
   ```powershell
   docker compose ps
   ```
- Check app logs:
   ```powershell
   docker compose logs -f app
   ```
- Stop stack:
   ```powershell
   docker compose down
   ```

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 4.0.2 (Spring MVC, Spring Data JPA)
- **API Documentation**: Springdoc OpenAPI (Swagger UI)
- **Validation**: Spring Boot Starter Validation (Bean Validation)
- **Observability**: Spring Boot Actuator (`health`/`info` public, rest ADMIN-only)
- **Database**: PostgreSQL
- **Schema migrations**: Flyway (versioned SQL migrations)
- **ORM**: Hibernate (JPA, `validate` mode)
- **Build Tool**: Maven 3.x (wrapper included)
- **Containerization**: Docker + Docker Compose
- **IDE**: The project is editor-agnostic, VS Code works fine.

## Contacts and Next Steps

- **Maintainer**: See `pom.xml` for project details

**Next steps (practical)**:

- Decide whether shipments should support partial fulfillment (would require shipment line items)
- Split the monolithic UI controller (API pagination/filtering is done)

**Last updated**: 2026-07-01
