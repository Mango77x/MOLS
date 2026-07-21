# MOLS — Project Overview

## Purpose

MOLS is a Spring Boot logistics app with:

- a REST API (JWT-secured) for CRUD operations
- a React SPA admin UI (`/app`) for day-to-day usage
- stock + movement auditing so changes are traceable — the movement log is
  **append-only** (never updated or deleted) and records **who** made each change

The goal of this doc is to help a developer understand where things live, how the flow works, and which business rules are enforced in the services.

## Project Status

- Runtime: Spring Boot 4.x on Java 21
- Database: PostgreSQL (local or via Docker Compose)
- API: `/api/**` (Swagger at `/swagger-ui.html`)
- UI: React SPA served at `/app/**` (see `frontend/`). The old Thymeleaf
  admin UI (`/ui/**`) was fully removed in Sprint 6 — `/ui/**` now just
  302-redirects to the `/app` equivalent for old bookmarks/links.
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

Security lives in `src/main/java/com/mls/logistics/security` (JWT for API and the SPA, delivered via an HttpOnly cookie).

### Frontend: React SPA

The new interface lives in `frontend/` (Vite + React 19 + TypeScript +
Tailwind 4) and is served at `/app/**`:

- **Auth**: cookie-based (the HttpOnly JWT cookie from `POST /api/auth/login`);
  session restored on page load via `GET /api/auth/me`; a 401 from any API
  call drops the client session and returns the user to the login page.
- **Shell**: sidebar/topbar layout, role-aware navigation (ADMIN-only entries
  hidden and route-guarded client-side; real enforcement is the API role
  matrix), light/dark theme, military-green design tokens in `src/index.css`.
- **Pages**: the dashboard (fed by `GET /api/dashboard`) ships KPI cards,
  Recharts charts (stock by warehouse, movements by type, orders by status —
  each with an empty-state fallback; the two donut charts also carry a
  custom text legend, Sprint 13, since a Recharts `<Tooltip>` alone only
  reveals slice colors on hover), a low-stock/stale-orders alerts panel
  and a recent-activity table (its Resource/Warehouse columns resolve
  names client-side via `useLookup`, Sprint 13, mirroring `MovementsPage`'s
  pattern — previously showed the raw `stockId`). Its hero section is the logistics map
  (`react-leaflet` + OpenStreetMap tiles, fed by `GET /api/map`): warehouse
  pins colored by stock status, unit pins, animated shipment routes, a
  details panel on pin click, and "active shipments only"/"low stock only"
  filters plus name search; renders a graceful empty state when no
  warehouse/unit has coordinates yet. Warehouses, Resources, Vehicles,
  Units, Stock, Orders, Shipments and the Audit log are server-paginated
  tables (`@tanstack/react-table` via a shared `DataTable`/`useServerTable`
  pair in `src/components/table/`) wired to the Sprint 0 pagination/sort/
  filter query params, with role-aware inline actions and, for
  Stock/Movements/Orders/Shipments, client-side lookups against the
  plain-array reference endpoints to resolve foreign ids to names. Orders
  rows expand inline to their line items (`GET /api/order-items?orderId=`).
  Below the `sm` breakpoint, `DataTable` swaps its `<table>` for a card list
  driven by the same `columns`/`data` props (Sprint 12) — one labeled row per
  column instead of a `<td>` — so the actions column (previously scrolled out
  of view on narrow viewports) is always visible; every page using `DataTable`
  picked this up automatically, no call-site changes needed.
  Users (`/app/users`, ADMIN-only) lists accounts, creates users, changes
  roles, resets passwords and enables/disables accounts against
  `/api/users/**` (Sprint 6). First-run setup (`/app/setup`) creates the
  initial ADMIN when the database has zero users, backed by
  `GET/POST /api/auth/setup-status` and `/api/auth/setup`.
- **Forms & detail pages** (Sprint 5): full create/edit flows built with
  `react-hook-form` + a `zod` resolver — shared field components live in
  `src/components/form/` (`TextField`/`SelectField`/`FormBanner`/buttons)
  plus `zodHelpers.ts` (numeric id/quantity/coordinate schema helpers, built
  with `z.custom` rather than `z.number()`/`z.preprocess` so an
  untouched/empty field shows a friendly message instead of zod's own
  "expected number, received NaN" — see the comment there for why).
  API errors are normalized by `api/errors.ts`: 400 validation failures map
  field-by-field onto the form via `setError`, 404/409 business conflicts
  (e.g. insufficient stock) surface as a banner. On success, every form calls
  `showToast(...)` (Sprint 12, e.g. `'Warehouse created.'`) right before
  navigating away — previously a create/edit just redirected silently, unlike
  `RowActions`'s delete flow which already toasted on both outcomes.
  - Warehouse/Resource/Vehicle/Unit/Stock (create + Stock's delta-based
    Adjust) are simple ADMIN-only forms; Warehouse/Unit include a
    click-to-place `LocationPicker` map (`src/components/map/`, reusing the
    dashboard map's tile layer) for latitude/longitude.
  - **Order wizard** (`pages/orders/OrderWizardPage.tsx`, ADMIN/OPERATOR):
    header → items → optional shipment. Items get a best-effort
    client-side stock-availability check (sums `GET /api/stocks?resourceId=`)
    that warns but doesn't block adding — the authoritative check is the
    atomic `POST /api/orders/with-items` call (new in Sprint 5, wraps the
    existing transactional `OrderService.createOrderWithItems`: an
    insufficient-stock conflict on any item leaves nothing persisted). The
    optional shipment step (Sprint 7) lets the user pick, per draft item, how
    much to ship now (defaults to the full ordered quantity); once the order
    succeeds, its real items are fetched and matched back to the drafts by
    resource id before `POST /api/shipments`, since a shipment's items
    reference real order-item ids. If shipment creation fails, the user lands
    on the order detail page with a banner rather than losing the created order.
  - **Order/Shipment edit + detail pages**: order edit is header-only plus
    an inline, immediately-persisted items manager (`OrderItemsManager.tsx`,
    mirroring the Thymeleaf inline-add/update/delete flow); shipment
    create/edit (`ShipmentFormPage.tsx`) includes an item picker — once an
    order is selected, its items are listed with ordered/delivered quantities
    and a "ship now" input capped at each item's remaining (unallocated)
    quantity — fixed at creation and replaceable as a whole set on edit while
    not yet `DELIVERED` (Sprint 7). Transitioning to `DELIVERED` deducts stock
    for the shipment's own items only and is rejected on insufficient stock,
    same as the API. Detail pages are read-only traceability views: order
    detail shows items (with per-item shipped/ordered progress)/shipments/
    linked movements, shipment detail shows context/this shipment's own items
    (not the full order)/linked movements — both link to each other
    (`Movement.shipmentId` → shipment detail) for end-to-end tracing. Order
    edit and `OrderItemsManager` (which takes a `locked` prop) lock down when
    the order is `COMPLETED`/`CANCELLED` — the status select is disabled and
    the items table drops to read-only — since the API rejects any change to
    either in that state anyway (Sprint 10).
  - `src/lib/enumLabels.ts` (Sprint 10) is the single source of truth for
    human-readable `OrderStatus`/`ShipmentStatus`/`VehicleStatus`/
    `VehicleType` labels — used by every filter dropdown, status badge, and
    form select, replacing what used to be a raw-enum-value display on list
    pages next to a separately hardcoded friendly-label copy on forms.
  - `src/components/ConfirmDialog.tsx` (Sprint 10) is a themed, portal-based
    confirmation modal (Escape/backdrop-click to cancel) used everywhere a
    destructive or consequential action needs confirming — row deletes,
    order-item removal, user role/enabled changes — replacing native
    `window.confirm()`.
  - A catch-all route renders `NotFoundPage` (Sprint 10) for any unmatched
    `/app/*` path, with redirect aliases for the two most guessable
    mismatches between a sidebar label and its actual route: `/app/stock` →
    `/app/stocks`, `/app/audit-log` → `/app/movements`.
- **Serving**: the production build is packaged into the jar at
  `static/app/` and served by `config/SpaWebConfig` with an `index.html`
  fallback for client-side routes.
- **Dev loop**: `npm run dev` inside `frontend/` (Vite proxies `/api` to
  `localhost:8080`); `npm run lint` (oxlint) and `npm test` (Vitest) run in
  the Maven build too. A `frontend-dev` launch config
  (`.claude/launch.json`) lets the Preview tooling run the Vite dev server
  standalone against an already-running backend for fast UI iteration.

### Dashboard (Operational)

The dashboard (`/app`) is a quick operational snapshot, fed by `GET /api/dashboard`. It shows:

- KPI cards with context (pending orders badge, fulfillment target flag, etc.)
- Charts (Recharts) with safe fallbacks when the database has little/no historical data
- A live logistics map (warehouse/unit pins, animated shipment routes)
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

**Security note**: `/api/**` is JWT-protected (cookie or header); `/api/users/**` and `/api/auth/register` are ADMIN-only, `/api/auth/setup-status` and `/api/auth/setup` are public (first-run only).

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
  - `V4` — stock reservation counter (`resources.reserved_quantity` at the time,
    `order_items.reservation_active`) so order-item creation commits demand instead of
    only checking current physical quantity
  - `V5` — `orders.warehouse_id` (fixed origin warehouse per order); moves the reservation
    counter from `resources` to `stocks.reserved_quantity` (per resource **and** warehouse)
  - `V6` — `shipment_items` (shipment ↔ order item + quantity junction table) and
    `PARTIALLY_SHIPPED` added to `orders.status`'s CHECK constraint, backing per-shipment
    partial fulfillment (see "Partial fulfillment" above)
  - `V7` — backfills `shipment_items` for shipments that predate V6 (which didn't
    populate it for existing rows), fixing `deliveredQuantity` reading 0 on old
    delivered shipments even though their stock was genuinely deducted at the time
  - `V8` — `app_users.password_version` (integer, default 0, bumped on every
    reset), backing JWT revocation on password reset. Deliberately a counter
    rather than a timestamp: a timestamp can only be compared at whatever
    precision it's embedded in the token at (a JWT's `iat` is whole-seconds),
    so two password-set events within the same second are indistinguishable
    once that precision is lost — an incrementing integer can't collide like
    that no matter how fast the events happen (two earlier timestamp-based
    versions of this both broke under fast sequential requests, e.g. login
    immediately followed by a reset)
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
    - Token revocation (Sprint 9): a syntactically valid, unexpired token is
      still rejected (401) by `JwtAuthFilter` if the account has since been
      disabled, or if its embedded `pwdVersion` claim no longer matches the
      user's current `app_users.password_version` (bumped on every reset) —
      otherwise disabling a user or resetting their password wouldn't take
      effect until the token's natural expiry (`SECURITY_JWT_EXPIRATION_MS`,
      24h by default). A role change is already effectively immediate, since
      authorities are re-derived from the DB on every request.
- **Brute-force protection**: after `SECURITY_LOCKOUT_MAX_ATTEMPTS` (default 5)
  consecutive failed logins a username is locked for
  `SECURITY_LOCKOUT_DURATION_MS` (default 15 min); all auth events are logged (`SECURITY:` lines).
- **Password policy**: minimum 12 characters (API register, admin user
  management, and first-run setup).
- **CSP**: `Content-Security-Policy` header on the `/app/**`-serving chain
  (`default-src 'self'`, plus the OpenStreetMap tile host for the logistics map).
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
4. Shipment delivery triggers fulfillment: transitioning a shipment to `DELIVERED` deducts
   stock (EXIT) and records movements for **that shipment's own items only** — not gated on
   sibling shipments of the same order also being delivered (see "Partial fulfillment" below).
5. The movement audit trail is **append-only**: no API or UI path can update or
   delete a movement; corrections are made with a new compensating adjustment.
   Deleting stock with movement history, delivered shipments, or completed
   orders is rejected to keep the trail coherent. Each movement records the
   acting user (`created_by`) via JPA auditing.
   - This is enforced by direct delete guards on `Order`/`Shipment`/`Stock`
     themselves, **and** by equivalent guards on `Unit`/`Vehicle`/`Warehouse`/
     `Resource` — none of the parent-side `@OneToMany` relations cascade a
     delete into their children anymore, so removing e.g. a warehouse that
     still has stock, orders, or shipments is rejected rather than silently
     cascading through them and orphaning `Movement` rows (Sprint 8).
6. Statuses follow explicit state machines (`OrderStatus`, `ShipmentStatus`):
   - Orders: `CREATED → VALIDATED → PARTIALLY_SHIPPED → COMPLETED`, or `→ CANCELLED` from
     `CREATED`/`VALIDATED` only (terminal states are final; `CREATED → COMPLETED` and
     `CREATED/VALIDATED → PARTIALLY_SHIPPED` are both allowed because fulfillment is
     shipment-driven, not a manual step; `PARTIALLY_SHIPPED` cannot be cancelled since stock
     has already physically left the warehouse for part of the order)
   - Shipments: `PLANNED ↔ IN_TRANSIT → DELIVERED` (DELIVERED is terminal)

### Partial fulfillment (shipment items)

A shipment carries a specific subset of its order's items, each with its own quantity
(`ShipmentItem`, table `shipment_items`) — fixed at creation (`POST /api/shipments` requires
a non-empty `items: [{ orderItemId, quantity }]`) and only replaceable as a whole set on
`PUT` while the shipment is not yet `DELIVERED`. A line's quantity can never exceed what's
still unallocated on that order item across every shipment (any status), mirroring the
existing stock-reservation math.

Delivering a shipment deducts stock/records movements only for its own items, releases the
matching *partial* portion of each order item's stock reservation
(`OrderItemService.releasePartialReservation`), and recomputes the parent order's status from
every item's cumulative delivered quantity: `COMPLETED` once every item is fully delivered,
`PARTIALLY_SHIPPED` once some (but not all) are. `OrderItemResponse` exposes `deliveredQuantity`
(sum across `DELIVERED` shipments) and `remainingQuantity` (order quantity minus allocation
across all shipments) so the UI can show shipped-vs-ordered progress per item.

### Current Enforcement Status

- ✅ Rule 1 implemented in `StockService.adjustStock()` and `StockService.createStock()`
   - Rejects negative initial quantity
   - Rejects adjustments that would produce negative stock (`InsufficientStockException`, HTTP 409)
- ✅ Rule 2 implemented in `OrderItemService` (create and update)
   - Rejects order items where requested quantity exceeds total available stock (`InsufficientStockException`, HTTP 409)
- ✅ Rule 3 implemented in `StockService.adjustStock()` and `StockService.createStock()`
   - Every stock increase/decrease records a `Movement` (`ENTRY`/`EXIT`) automatically
- ✅ Rule 4 implemented in `ShipmentService` on transition to `DELIVERED`
   - Deducts stock per shipment item and records `EXIT` movements
   - Prevents invalid transitions (e.g., reverting DELIVERED)
   - Order-level fulfillment status (`PARTIALLY_SHIPPED`/`COMPLETED`) derived per delivery

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
├── controller/      # REST API controllers (10 files, incl. UserController)
├── domain/          # JPA entities (9 entities)
├── repository/      # Spring Data repositories (9 interfaces)
├── service/         # Business logic services (9 services)
├── security/        # JWT auth, users, roles, and security config
├── exception/       # Global exception handling and error contracts
├── dto/             # Request/response DTO contracts
├── config/          # OpenAPI, SPA serving (SpaWebConfig), and the
│                    # LegacyUiRedirectController (/ui/** -> /app/**)
└── LogisticsApplication.java

src/main/resources/
└── static/app/       # Built React SPA (from frontend/dist), served at /app
```

The Thymeleaf `web` package and `templates/` were fully removed in Sprint 6
once the React SPA reached feature parity — see git history before this
sprint if you need to reference the old server-rendered pages.

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
- **Frontend tests** (`frontend/src/**/*.test.{ts,tsx}`, Vitest + jsdom +
  `@testing-library/react`) — 19 files / 56 tests covering pure helpers
  (enum labels, roles) and DOM/behavior tests (form validation, the
  `ConfirmDialog`/`RowActions` delete flow including a failed-delete-shows-
  a-toast regression guard, the order-edit terminal-state lock, app
  routing/redirects, the Sprint 12 success-toast-on-save for every form page,
  `DataTable`'s mobile card view, and the Sprint 13 dashboard name-resolution/
  donut-legend fixes).

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

Additional order operation:
- `POST /api/orders/with-items` — creates an order and its line items in a
  single transaction (ADMIN or OPERATOR, same rule as other order writes).
  Body is `{ header: CreateOrderRequest, items: [{ resourceId, quantity }] }`;
  reuses `OrderService.createOrderWithItems`, so an insufficient-stock
  conflict (409) or validation error (400) on any item leaves nothing
  persisted. Backs the React order wizard's atomic create step.

Shipment items:
- `POST /api/shipments` requires a non-empty `items: [{ orderItemId, quantity }]`
  alongside `orderId`/`vehicleId`/`status` — a shipment always carries at
  least one order item. `PUT /api/shipments/{id}` accepts the same `items`
  field to replace the shipment's whole item set (omit it to leave items
  unchanged); replacing is rejected once the shipment is `DELIVERED`. A
  line's quantity is capped by that order item's remaining (unallocated)
  quantity across every shipment. `ShipmentResponse.items` and
  `OrderItemResponse.deliveredQuantity`/`remainingQuantity` expose the
  resulting allocation/progress — see "Partial fulfillment" above.

Dashboard endpoint:
- `GET /api/dashboard` — aggregated operational snapshot (KPIs, chart series,
  low-stock/stale-order alerts, recent movements and the thresholds used),
  assembled by `DashboardService` and readable by any authenticated role. This
  is the API counterpart of the `/ui` dashboard for the React frontend.

Map endpoint:
- `GET /api/map` — geo snapshot for the logistics map: warehouse pins (with
  an OK/WARNING/CRITICAL stock status derived from the same low/critical
  stock thresholds as the dashboard) and unit pins, plus shipment routes
  resolved from the shipment's origin warehouse to its order's destination
  unit. Assembled by `MapService`, readable by any authenticated role.
  Warehouses/units without coordinates, and shipment routes with either
  endpoint missing coordinates, are omitted — the map can only plot what has
  a location.

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

**Last updated**: 2026-07-21 (Sprint 13: dashboard polish — `RecentActivity`
now resolves stock ids to resource/warehouse names client-side (mirroring
`MovementsPage`'s lookup pattern) instead of showing a raw `#id`, and both
donut charts (`movements by type`, `orders by status`) gained a custom
text legend so slice colors are readable without hovering. Frontend suite
grew from 17 files / 51 tests to 19 files / 56 tests — see
`docs/DEVELOPMENT_PLAN.md` for the full technical-debt and
product-completion backlog)
