# MOLS UI Migration — Execution Planning

> Companion to [UI_IMPROVEMENT_PLAN.md](UI_IMPROVEMENT_PLAN.md) (the *what*). This document is the *when/how*: sprint breakdown, backend prerequisites discovered in the codebase, and decision points. Both documents are living blueprints and should be deleted once the migration ships.

**Strategy**: Option A (incremental) as recommended in the plan. React runs alongside Thymeleaf; pages migrate one at a time; Thymeleaf is deprecated only when React reaches feature parity.

---

## Workflow Conventions (every sprint follows these)

Established across Sprints 0–4 and expected to continue through Sprint 6:

- **Branch per sprint**: work happens on a `sprint-N` branch cut from `main` (after confirming
  the previous sprint's PR is merged), pushed and opened as a PR against `main`. The PR is merged
  by the repo owner via GitHub, not by the agent doing the work.
- **Commit style**: one commit per sprint (imperative summary line), with a body explaining what
  changed and why, plus a closing note on how it was verified. **No `Co-Authored-By: Claude`
  trailer** — omit it entirely in this repo.
- **Docs updated alongside code, every sprint**:
  - `PROJECT_OVERVIEW.md` — update the relevant section(s) with concrete detail of what was
    actually built (new endpoints, components, config), not left as generic pre-sprint wording.
  - This file (`docs/UI_MIGRATION_PLANNING.md`) — check off the sprint's checklist items,
    rewriting the bullets to describe what was actually implemented.
  - `README.md` / `HELP.md` — only touched when something user-facing changed (new URL, new tech
    in the stack list, new run instructions).
- **Tests**: backend changes get unit + integration tests per change. Frontend changes only add
  tests for pure-logic modules (hooks/helpers with no JSX); page/component code is verified
  manually instead of unit-tested, matching the pattern so far.
- **Verification**: every sprint is checked against the actual running app — rebuild and restart
  the `docker compose` stack (`docker compose up --build -d`), log in, and exercise the real
  feature in a browser — not just build/lint/test passing. Manual checks have caught real bugs
  that automated checks alone missed (e.g. Sprint 4's pagination-envelope and missing-key bugs).
- **`gh` CLI is not installed** on the machine these sprints are built on — after pushing, hand
  back the PR creation URL GitHub prints (`https://github.com/Mango77x/MOLS/pull/new/sprint-N`)
  instead of trying to open the PR programmatically.

---

## Codebase Audit — Gaps Blocking the Migration

These were found by reading the current code and must be addressed **before or during Sprint 0**, otherwise the React app has nothing correct to consume:

| # | Gap | Evidence | Impact on React |
|---|-----|----------|-----------------|
| 1 | **API role matrix ≠ UI role matrix** | API: all writes `hasRole("ADMIN")` (`SecurityConfig.apiSecurityFilterChain`). UI: `OPERATOR` may create/edit orders & shipments via session form-login | A React app consuming `/api/**` locks OPERATORs out of their core workflow. Must extend API rules (orders/shipments write → `ADMIN` or `OPERATOR`) + integration tests for the new matrix |
| 2 | **No pagination/filtering anywhere** | All list endpoints return `findAll()` | Smart tables (Phase 4) need server-side `page/size/sort/filter`. Also a pre-existing roadmap item |
| 3 | **No dashboard API** | KPIs/charts/alerts are computed in services but only exposed through `UiController` (2006 lines) → Thymeleaf model | Need `GET /api/dashboard` (or split endpoints) returning the aggregates as JSON. Good moment to move aggregations to SQL (`SUM`/`GROUP BY`) as already planned |
| 4 | **No coordinates on Warehouse/Unit** | `Warehouse`/`Unit` entities only have free-text `location` | Map (Phase 3) requires `latitude`/`longitude`: Flyway `V3` migration, entity/DTO/validation changes (range checks −90..90 / −180..180), UI form fields |
| 5 | **User management is UI-only** | `UiUsersController` + `AppUserAdminService`; no `/api/users` | Needed only when migrating the Users page (late phase) — build the admin REST endpoints then, not now |
| 6 | **SPA auth undecided** | JWT is 24 h, no refresh/revocation; no CORS config; UI uses session+CSRF | See Decision D1 below |
| 7 | **First-run setup & login pages** | `/ui/setup`, `/ui/login` are server-rendered and session-based | Keep in Thymeleaf until last; React app can redirect to them meanwhile |

---

## Decisions to Lock Before Sprint 1

### D1 — SPA authentication (security-critical, portfolio-visible)

**Recommendation: same-origin + HttpOnly cookie.**

- **Dev**: Vite dev server proxies `/api` to `localhost:8080` (`server.proxy`) → no CORS at all.
- **Prod**: React build is copied into the Spring Boot jar (`static/app/`) or served by the same reverse proxy → same origin, no CORS.
- **Token transport**: on login, set the JWT in an `HttpOnly; Secure; SameSite=Strict` cookie instead of returning it for `localStorage` (XSS-resistant — the defensible choice for a defense-sector portfolio). `JwtAuthFilter` reads header **or** cookie. CSRF protection stays meaningful → keep CSRF tokens for cookie-authenticated mutating calls (or use a custom header requirement).
- Rejected: `localStorage` JWT (XSS exposure), wide-open CORS (unnecessary once same-origin).

### D2 — Where the frontend lives & builds

**Recommendation**: `frontend/` at repo root; `frontend-maven-plugin` (or a CI step) builds it and copies `dist/` into the jar during `mvn package`. CI adds a `node` step: `npm ci && npm run lint && npm run test && npm run build`.

### D3 — Route split during incremental rollout

React app mounts at `/app/**` (new chain in `SecurityConfig`); Thymeleaf keeps `/ui/**`. Navbar links cross over per page as each migrates. Delete `/ui` pages only at the end.

---

## Sprint Plan

Estimates follow the plan's ~21-day budget, re-sequenced so backend prerequisites come first and the map (70% wow factor) lands early.

### Sprint 0 — Backend prerequisites (4–5 days)
> No React code yet. Everything here is independently valuable even if the migration stalls.

- [x] Align API role matrix with UI roles (orders/shipments writes for OPERATOR) + extend `SecurityRolesIntegrationTest`
- [x] Pagination + filtering on list endpoints (`Pageable`, spec-based filters; non-paged plain-array behavior kept for existing consumers)
- [x] `GET /api/dashboard` endpoint: aggregation assembly extracted into a `DashboardService` (SQL-side aggregation of the remaining in-memory paths moves with the UiController refactor)
- [x] Flyway `V3`: `latitude`/`longitude` (nullable `double precision`) on `warehouses` and `units`; entity + DTO + Bean Validation ranges; exposed in existing Thymeleaf forms so data can be captured immediately
- [x] D1 implementation: cookie-capable `JwtAuthFilter` + login sets HttpOnly SameSite=Strict cookie, logout clears it
- Exit criteria: all existing tests green, new integration tests for role matrix and pagination, JaCoCo floor holds

### Sprint 1 — Scaffold + auth + shell (2–3 days)

- [x] `npm create vite@latest frontend -- --template react-ts`; Tailwind 4, React Router, Zustand, Axios (401 interceptor drops the session → login)
- [x] Design tokens: military-green palette, status colors (green/yellow/red), dark/light theme (class-based)
- [x] App shell: sidebar/topbar layout, protected routes, login flow against the cookie-based auth (plus new `GET /api/auth/me` for session restore), role-aware nav (ADMIN-only entries hidden and route-guarded)
- [x] Maven/CI integration (D2): `mvn package` builds the SPA (frontend-maven-plugin, pinned Node) into the jar at `static/app`, served at `/app` with SPA fallback (`SpaWebConfig`); CI lints/tests/builds the frontend inside `mvnw verify`; `-Dskip.frontend=true` for backend-only loops
- Exit criteria: `docker compose up` serves the React shell with working login/logout — verified against the packaged jar

### Sprint 2 — Dashboard (3 days)

- [x] KPI cards with state colors + trend indicator (fulfillment rate vs. target), fed by `GET /api/dashboard`
- [x] Charts (`recharts`, newly added dependency): stock by warehouse (bar), movements by type + orders by status (donut with center total) — each with a "No data to display" empty state, matching the Chart.js fallbacks on `/ui`
- [x] Alerts panel: low-stock cards (critical vs. warn tone, deep-link to the existing `/ui/stocks/{id}/adjust` page) and stale-orders cards (tone scales with days pending); recent-activity table (last 15 movements) rounds out parity with the Thymeleaf dashboard
- Exit criteria: React dashboard at parity with the Thymeleaf one + visibly better — verified against the running `docker compose` stack (KPIs, charts, empty states, alerts and activity table all render correctly in light/dark)

### Sprint 3 — Logistics map ⭐ (3–4 days)

- [x] New `GET /api/map` backend endpoint (`MapService` + `MapController`): resolves warehouse/unit
  pins with coordinates and shipment routes (origin warehouse → destination unit via the order),
  omitting any entry missing coordinates; warehouse stock status (OK/WARNING/CRITICAL) reuses the
  dashboard's low/critical thresholds so pin colors agree with the dashboard's alerts
- [x] `react-leaflet` + OpenStreetMap tiles (added as new frontend dependencies)
- [x] Warehouse pins (color = stock status), Unit pins (distinct diamond icon)
- [x] Shipment routes source → destination, styled by status (PLANNED dashed / IN_TRANSIT dashed +
  animated dash-offset / DELIVERED solid, faded)
- [x] Click pin → details panel; "active shipments only" / "low stock only" filters; name search
  with fly-to
- [x] Coordinates captured through the existing Thymeleaf warehouse/unit forms (Sprint 0); no
  migration-seeded demo data — the map's own empty state covers the zero-coordinate case
- Deferred (as per plan): real routing, traffic/weather overlays
- Exit criteria: map is the dashboard's hero section and works with zero-coordinate data (graceful
  empty state) — verified against the running `docker compose` stack with real warehouse/unit/shipment
  data

### Sprint 4 — Data tables (4–5 days)

- [x] Generic table component on `@tanstack/react-table` (`DataTable` + `useServerTable` in
  `frontend/src/components/table/`): server-side pagination/sort/filter, always requesting the
  `PageResponse` envelope, wired to the Sprint 0 endpoints
- [x] Migrated list pages in order of simplicity: Warehouses → Resources → Vehicles → Units →
  Stocks (Adjust action, resource/warehouse names resolved via lookups) → Movements (read-only
  audit, resolved through stock→resource/warehouse lookups, default sort newest-first) → Orders
  (expandable line items via `GET /api/order-items?orderId=`) → Shipments
- [x] Inline actions with role-aware visibility (edit links to the existing `/ui/**` forms;
  ADMIN-only for Warehouses/Resources/Vehicles/Units/Stock; ADMIN+OPERATOR edit / ADMIN-only
  delete for Orders/Shipments, matching the API role matrix) and a native `confirm()` dialog
  before every delete, mirroring the Thymeleaf UI's own confirm pattern
- Exit criteria: every list page usable in React; Thymeleaf equivalents still available as
  fallback — verified against the running `docker compose` stack with real seeded data (filters,
  sorting, pagination, lookups, order-item expansion and role-gated actions all checked)

### Sprint 5 — Forms & detail pages (4–5 days)

- [x] `react-hook-form` + `@hookform/resolvers/zod`; shared field components
  (`TextField`/`SelectField`/`FormBanner`/buttons in
  `frontend/src/components/form/`) with real-time (on-blur/on-change)
  validation. `zodHelpers.ts` provides numeric id/quantity/coordinate schema
  builders using `z.custom` — deliberately not `z.number()`/`z.preprocess`,
  which either reject `NaN` before a custom message can apply or widen the
  resolver's input type and break `tsc -b`'s project-reference build.
- [x] CRUD forms: warehouse + unit (with a click-to-place `LocationPicker`
  map for lat/long, `frontend/src/components/map/`, reusing the dashboard
  map's tile layer), resource, vehicle, stock create + delta-based adjust —
  all ADMIN-only, matching the API/UI role matrix.
- [x] Order wizard (`OrderWizardPage.tsx`, ADMIN/OPERATOR): header → items →
  optional shipment. Items get a best-effort client-side stock-availability
  check (non-blocking warning); the atomic `POST /api/orders/with-items`
  (new endpoint, Sprint 5 backend addition wrapping the existing
  transactional `OrderService.createOrderWithItems`) is the authoritative
  check — an insufficient-stock conflict leaves nothing persisted. The
  optional shipment is a follow-up `POST /api/shipments` call; a failure
  there still lands the user on the order detail page (order already
  created) with a banner instead of losing their work.
- [x] Order edit (header + inline, immediately-persisted items manager) and
  Shipment create/edit (single form covering the
  PLANNED→IN_TRANSIT→DELIVERED transition, which deducts stock and can 409)
  round out the write side.
- [x] Detail pages: order (items/shipments/linked movements) and shipment
  (context/order items/linked movements), cross-linked for traceability —
  mirrors the Thymeleaf `order-detail`/`shipment-detail` layouts.
- Exit criteria: full create/edit flows in React, HTTP 409/400 business
  errors surfaced as friendly messages (field-level for validation, banner
  for conflicts) — verified against the running `docker compose` stack
  (production jar) and, for faster iteration, a standalone Vite dev server
  against the same backend. Two real bugs were caught only by that manual
  pass (not by `tsc`/lint/tests): a Leaflet crash on `NaN` coordinates, and
  shipment/order edit forms silently mis-selecting the Order/Unit dropdown
  because `reset()` raced an async lookup fetch — both fixed.

### Sprint 6 — Parity, polish & cutover (3 days)

- [ ] Users admin page (build `/api/users` admin endpoints now — gap #5)
- [ ] Polish pass: loading skeletons, empty states, toasts, keyboard nav, responsive audit (mobile sidebar)
- [ ] A11y & security pass: CSP headers for the SPA, dependency audit, axe check
- [ ] Cutover: redirect `/ui/**` pages to React equivalents, remove dead Thymeleaf templates, raise JaCoCo floor (UiController shrinks), update README/PROJECT_OVERVIEW screenshots
- [ ] Delete `UI_IMPROVEMENT_PLAN.md` and this file (per their own notes)
- Optional stretch: Kanban board for shipments (Phase 6) — only if the core is polished

---

## Total: ~23–28 days (vs. plan's ~21) — the delta is Sprint 0, which the original plan under-scoped as "Backend Prep".

## Risks

- **Spring Boot 4 modularity**: new deps (e.g. CORS/resource handling) may need dedicated modules — same class of gotcha already hit with `spring-boot-flyway`.
- **Double maintenance window**: while both UIs live, every feature change touches two frontends. Mitigate by freezing Thymeleaf (bugfixes only) once Sprint 2 lands.
- **Coverage gate**: adding a frontend doesn't move JaCoCo, but deleting `UiController` code at cutover will raise real coverage — plan the ratchet then.
- **Map with empty data**: demo needs seeded coordinates or it undersells the differentiator.

## Success Metrics

Unchanged from the plan: "looks professional", map is the first thing seen, tables feel instant, forms guide the user, zero performance issues.
