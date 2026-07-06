# MOLS UI Migration — Execution Planning

> Companion to [UI_IMPROVEMENT_PLAN.md](UI_IMPROVEMENT_PLAN.md) (the *what*). This document is the *when/how*: sprint breakdown, backend prerequisites discovered in the codebase, and decision points. Both documents are living blueprints and should be deleted once the migration ships.

**Strategy**: Option A (incremental) as recommended in the plan. React runs alongside Thymeleaf; pages migrate one at a time; Thymeleaf is deprecated only when React reaches feature parity.

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

- [ ] Align API role matrix with UI roles (orders/shipments writes for OPERATOR) + extend `SecurityRolesIntegrationTest`
- [ ] Pagination + filtering on list endpoints (`Pageable`, spec-based filters; keep non-paged behavior for existing consumers or version the change)
- [ ] `GET /api/dashboard` endpoint: extract aggregation logic out of `UiController` into a `DashboardService`, convert in-memory aggregations to SQL
- [ ] Flyway `V3`: `latitude`/`longitude` (nullable `double precision`) on `warehouses` and `units`; entity + DTO + Bean Validation ranges; expose in existing Thymeleaf forms so data can be captured immediately
- [ ] D1 implementation: cookie-capable `JwtAuthFilter` + login endpoint sets HttpOnly cookie
- Exit criteria: all existing tests green, new integration tests for role matrix and pagination, JaCoCo floor holds

### Sprint 1 — Scaffold + auth + shell (2–3 days)

- [ ] `npm create vite@latest frontend -- --template react-ts`; Tailwind, React Router, Zustand, Axios (interceptors for 401 → redirect)
- [ ] Design tokens: military-green palette, status colors (green/yellow/red), 8px grid, dark/light theme
- [ ] App shell: sidebar/topbar layout, protected routes, login flow against the cookie-based auth, role context (hide ADMIN-only nav for OPERATOR/AUDITOR)
- [ ] Maven/CI integration (D2): `mvn package` produces a jar containing the SPA; CI lints/tests/builds the frontend
- Exit criteria: `docker compose up` serves the React shell with working login/logout

### Sprint 2 — Dashboard (3 days)

- [ ] KPI cards with state colors + trend indicators, fed by `GET /api/dashboard`
- [ ] Charts (Recharts): stock by warehouse, movements by type, orders by status — with empty-state fallbacks like the current dashboard
- [ ] Alerts panel: low stock / stale orders as actionable cards (deep-link to adjust/detail)
- Exit criteria: React dashboard at parity with the Thymeleaf one + visibly better

### Sprint 3 — Logistics map ⭐ (3–4 days)

- [ ] `react-leaflet` + OpenStreetMap tiles
- [ ] Warehouse pins (color = stock status from dashboard data), Unit pins (distinct icon)
- [ ] Shipment lines source → destination, styled by status (PLANNED/IN_TRANSIT/DELIVERED); animate IN_TRANSIT
- [ ] Click pin → details sidebar; filters (active shipments, low stock); search/zoom
- [ ] Seed realistic demo coordinates for the portfolio demo
- Deferred (as per plan): real routing, traffic/weather overlays
- Exit criteria: map is the dashboard's hero section and works with zero-coordinate data (graceful empty state)

### Sprint 4 — Data tables (4–5 days)

- [ ] Generic table component on `@tanstack/react-table`: server-side pagination/sort/filter wired to Sprint 0 endpoints
- [ ] Migrate list pages in order of simplicity: Warehouses → Resources → Vehicles → Units → Stocks (incl. adjust action) → Movements (read-only audit) → Orders (expandable items) → Shipments
- [ ] Inline actions with role-aware visibility; confirm dialogs for deletes
- Exit criteria: every list page usable in React; Thymeleaf equivalents still available as fallback

### Sprint 5 — Forms & detail pages (4–5 days)

- [ ] `react-hook-form` + zod resolver; shared field components with real-time validation
- [ ] CRUD forms: warehouse (with map picker for lat/long), resource, vehicle, unit, stock create/adjust
- [ ] Order wizard (order → items → optional shipment) with stock-availability feedback
- [ ] Detail pages: order + shipment with linked movements (traceability views)
- Exit criteria: full create/edit flows in React, HTTP 409 business errors surfaced as friendly messages

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
