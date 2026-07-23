# MOLS — Development Plan: Post-Audit Roadmap

## Context

Sprints 1-14 (backend/frontend build-out, then a technical-debt + product-completion pass) are complete and merged — see git history for that plan's full content (`docs/DEVELOPMENT_PLAN.md` as of commit `7727a3e` had the Sprint 8-14 detail before it was removed as fully done).

After Sprint 14, the app went through a second hands-on product audit (live, in-browser, trying to break things — not a code review) to check whether it held up as a real, usable tool rather than just "tests pass." That audit found the core (data integrity, stock reservation, role guards, session revocation) genuinely solid, but flagged a tier of missing capability between "MVP" and "professional software a team could run on daily without hand-holding." This plan tracks that follow-up work, split by how big a bet each tier is:

- **Nivel 0 — bugs found during the audit.** Small, unambiguous fixes. **Done (Sprint 15).**
- **Nivel 1 — table stakes for a real deployment.** Internationalization, data export, notifications, bulk import. This is where the immediate sprint backlog below lives.
- **Nivel 2 — commercial ERP surface.** Approval workflows, barcode scanning, custom reporting, third-party integrations, a real public API. Recommended, not yet scheduled — see the backlog section at the bottom.
- **Nivel 3 — multi-tenant SaaS infrastructure.** Only relevant if the goal becomes selling to multiple customers from one instance, not running MOLS for a single organization. Not recommended to start until that's an explicit decision — see the backlog section.

Same workflow as before: one `sprint-N` branch per sprint, opened as a PR against `main`, merged by the maintainer. No AI/Claude co-author trailer on any commit. This doc is checked off sprint by sprint, alongside `PROJECT_OVERVIEW.md`'s "Last updated" line.

---

## Nivel 0 — audit fixes (done)

### Sprint 15 — closed two gaps found in the live re-audit

- [x] `LoginPage`: a user who loses access and isn't the one first-run admin had zero path forward beyond "Invalid username or password." (`/app/setup` only stays reachable while zero application users exist). Added a static, security-safe hint pointing to the system administrator.
- [x] `useDuplicateNameWarning`: the lookup only checked the first 20 fragment-matched results for an exact duplicate, so a real duplicate past that page on a larger catalog went undetected. Bumped to 100 (the app's own established "large page" convention).

---

## Nivel 1 — table stakes for a real deployment

### Internationalization (i18n) — Sprints 16-17

Scoped from a real count against the current codebase, not a guess: ~35-40 of 69 frontend source files have hardcoded English strings (53 table-header literals across 10 files, `showToast(...)` in 10 files), ~67 hardcoded backend exception messages across 17 files (many interpolate raw values, e.g. `ShipmentService`'s `"Shipment must include at least one item. Shipment id: " + id`), and 15 test files / 44 assertions query the DOM by hardcoded English text. No i18n library is installed on either side today.

**Languages**: English (existing text becomes the source locale) + Spanish + French to start. Architecture is designed so a language with fundamentally different rules — Arabic (RTL, 6-way plural), Chinese/Vietnamese/Malay (no plural distinction) — can be added later without revisiting the plumbing, without actually building or QA'ing support for those languages now (see the specific "future-proofing, not gold-plating" decisions below — mixing those two would be over-engineering for languages nobody asked for yet).

**Backend error messages**: chose the lower-invasion path over Spring's `MessageSource`/`.properties` bundle. Custom exceptions (`ResourceNotFoundException`, `InvalidRequestException`, `InsufficientStockException`, `DuplicateResourceException`) and `GlobalExceptionHandler`'s own messages gain a machine-readable `code` (+ optional `params` map), while the existing free-text `message` stays as the English fallback for backward compatibility. The frontend translates recognized codes and falls back to displaying `message` as-is for anything not yet migrated — this lets error-message coverage roll out incrementally (start with the handful of codes a real user actually hits: stock reservation, duplicate name, delete guards, last-admin) instead of blocking on converting all ~67 in one sprint. Translation authorship stays in one place (the frontend's resource files) instead of split across Java `.properties` and React JSON.

#### Sprint 16 — i18n infrastructure + shared UI surface

- [x] Added `react-i18next` + `i18next`. Dropped `i18next-browser-languagedetector` from the original plan — `useLocale.ts` already owns detection/persistence itself (see below), and running a second automatic-detection plugin alongside it would just be two systems that could disagree about the active locale. Swapped `i18next-parser` (deprecated upstream, points at `i18next-cli` as its replacement) for `i18next-cli` directly — `frontend/i18next.config.ts` + `npm run i18n:status`/`i18n:extract`.
- [x] `frontend/src/i18n/` — single-namespace `en.json`/`es.json`/`fr.json`, plus `index.ts` wiring `i18next.use(initReactI18next).init(...)`, `SUPPORTED_LOCALES`/`LOCALE_LABELS`/`RTL_LOCALES` (the last one empty today, on purpose).
- [x] `useLocale.ts` — mirrors `useTheme.ts` exactly: reads/persists `mols-locale` in `localStorage`, falls back to the browser language's primary subtag, sets `document.documentElement.lang` and `dir` (off `RTL_LOCALES`).
- [x] Language switcher (a `<select>`) in `AppLayout`'s topbar, next to the theme toggle.
- [x] `enumLabels.ts` refactor: the four label maps hold i18next keys now, plus a new `ROLE_LABELS` map (not in the original plan — `AppLayout`'s user-role badge needed one and it's the same pattern). `enumLabel()` resolves through the global `i18n` instance directly (not the `useTranslation()` hook), so it stays callable from non-component code.
- [x] `RecentActivity.tsx` and `MovementsPage.tsx`'s `formatDateTime` now take the active i18next locale explicitly instead of `toLocaleString(undefined, ...)`.
- [x] **Correctness fix, not just translation**: `DataTable.tsx`'s result-count caption *and* the pagination-range summary below it both moved to i18next's `count`-based plural keys (`results_one`/`results_other`) instead of hand-rolled ternaries — French's 0-is-singular rule ("0 résultat") differs from English/Spanish's, so the old pattern would have shipped wrong French. Verified live in Sprint 16's own manual check: `2 résultats` / `1–2 sur 2` render correctly in French.
- [x] Extracted strings from the shared surface: nav labels (`nav.ts`'s `label` field became a `labelKey` resolved with `t()`), `ConfirmDialog`, `DataTable` chrome, `RowActions`, `NotFoundPage`, `LoginPage`, and the Dashboard's shared chrome (`DashboardPage`, `KpiCards`, `AlertsPanel`, `Charts`, `RecentActivity`). `FormBanner`/`FieldError`/`TextField`/`SelectField` turned out to already have zero hardcoded strings of their own (everything is caller-supplied props) — nothing to extract there.
- [x] Found and fixed in passing while already touching the line for translation: `AlertsPanel.tsx`'s low-stock "Adjust" link used a stale `/ui/stocks/.../adjust` URL (pre-React-migration route) instead of the real `/app/stocks/.../adjust` SPA route.
- [x] Logical Tailwind properties adopted in the files touched this sprint (no dedicated sweep of untouched files).
- [x] Tests: i18next reset to English in `src/test/setup.ts` after every test (`afterEach`) — all pre-existing English-text assertions kept passing unchanged. Added `useLocale.test.ts`, an `AppLayout.test.tsx` proving the language switcher re-renders nav labels end-to-end, extended `enumLabels.test.ts` (locale-aware resolution + a completeness check that every map value is a real, resolvable key), and extended `DataTable.test.tsx` with the French 0/1/2+ plural-boundary cases. Frontend suite: 21 files / 74 tests → 23 files / 86 tests.
- [x] Docs: `PROJECT_OVERVIEW.md`.

**Fixup (found in real usage right after merge, not by a test):** the Sprint 16 `enumLabels.ts` refactor changed what the four label maps *hold* (i18next keys instead of literal text), but 8 call sites across `OrderEditFormPage`, `OrdersPage`, `ShipmentStep`, `ShipmentFormPage`, `ShipmentsPage`, `VehicleFormPage` (×2), `VehiclesPage` populated their status/type `<select>` options via `Object.entries(XXX_LABELS).map(([value, label]) => ...)`, rendering the destructured `label` directly instead of calling `enumLabel()` — those dropdowns started showing the raw key (e.g. `enums.shipmentStatus.PLANNED`) instead of "Planned". Fixed all 8 by routing through `enumLabel(MAP, value)` like every other consumer already did. Also translated `LogisticsMap.tsx`, missed from the Dashboard's shared-chrome pass despite being explicitly in scope. Both found by the maintainer testing the merged build in the browser, not by the test suite — worth remembering that "populates a dropdown from a label map" needed the same code-search sweep the plan already did for `enumLabel()` call sites, not just those call sites themselves.

#### Sprint 17 — i18n: remaining pages + backend error codes + ES/FR content

- [x] Extract the remaining page-specific strings: the 9 list/form page pairs (Warehouses, Resources, Vehicles, Units, Stock, Orders, Shipments, Movements, Users) — filters, column headers, field labels/hints, wizard step copy.
- [x] Backend: added `code` (+ `params` where the message interpolates a value) to the custom exceptions (`CodedException` base class) and `GlobalExceptionHandler`'s 4 handlers, across 17 throw sites (stock reservation/adjust/delete, delete-integrity guards on Warehouse/Resource/Unit/Vehicle/Order, "can't disable/remove the last ADMIN"). The long tail of the remaining messages keeps falling back to English via `message` until migrated opportunistically later.
- [x] Frontend: extended `ApiErrorBody`/`extractApiError` to carry `code`/`params`; translates via `i18n.t('errors.' + code, params)` when the code is recognized, otherwise shows `message` unchanged.
- [x] Wrote full Spanish and French content for everything extracted across Sprint 16 and 17 — `npm run i18n:status` reports 100% ES/FR coverage (340/340 keys).
- [x] Tests: `errors.test.ts` covers the code-based `extractApiError` path (recognized code → translated with params, unrecognized/missing code → English fallback, fieldErrors priority preserved). Spot coverage on migrated pages: the pre-existing DOM-behavior tests (`WarehouseFormPage`, `ResourceFormPage`, `VehicleFormPage`, `UnitFormPage`, `StockCreateFormPage`, `StockAdjustFormPage`, `OrderEditFormPage`, `ShipmentFormPage`, `UserFormPage`) all still pass unchanged against the translated components, since they assert against the English locale's resolved text.
- [x] Docs: `PROJECT_OVERVIEW.md`. No `HELP.md` change needed — the language switcher was already documented in Sprint 16; Sprint 17 only extended coverage, it didn't change end-user-visible behavior.

**Found and fixed during the mandatory live Docker verification (not by any test):**
- `OrderDetailPage.tsx` interpolated the English word "from" as a literal (`{unit?.name} • from {warehouse.name}`) instead of `t('orders.from')` — showed untranslated in ES/FR.
- `FormPage.tsx` (the shared create/edit page chrome used by all 9 form pages) hardcoded `backLabel = 'Back'` as its default prop — every single form's "Back" link was English-only in every locale. No caller ever overrode it, so this silently affected the entire app; fixed by resolving the default through `t('common.back')` inside the component instead.
- `ShipmentFormPage.tsx`'s "items to ship" section rendered a stray literal `NaN` text node on the new-shipment form before an order is selected: `{selectedOrderId && orderItems.length === 0 && (...)}` — when the order `<select>` is unselected, React Hook Form's `valueAsNumber` transform yields `NaN` (not `undefined`) for the empty string, and JS `&&` returns the first falsy operand (`NaN`) rather than `false`, which React renders as text. Pre-existing bug (same logic before Sprint 17, only the copy changed), not introduced by this sprint — fixed with an explicit `Boolean(selectedOrderId)` coercion.

### Sprint 18 — CSV/Excel export

- [x] Export action on `DataTable`-backed list pages: client-side CSV generation, reusing the existing paginated endpoints rather than adding an uncapped backend export path. `fetchAllPages()` (`frontend/src/api/fetchAllPages.ts`) loops at the server's own `PageQuery.MAX_SIZE` (100) cap, honoring the page's current filters/sort so the export matches what the operator has filtered to, not just the page on screen. `frontend/src/lib/csv.ts` handles RFC-4180-ish escaping (quotes/commas/newlines) and triggers the browser download with a UTF-8 BOM (Excel mis-renders accented ES/FR text without it). A shared `ExportCsvButton` (`components/table/ExportCsvButton.tsx`) owns the loading state and an error toast.
- [x] Scope: Stock, Movements, Orders — the three pages an operator actually needs for reporting/backup — rather than all nine at once, per the plan. Each page's CSV columns mirror its on-screen table (plain-text values, not the `<Badge>`/JSX cells), translated via the active locale's `t()` and `enumLabel()`.
- [x] Tests: `csv.test.ts` (escaping rules), `fetchAllPages.test.ts` (pagination loop termination, filter/sort forwarding, the size=100 cap, defensive stop on an inconsistent empty-page response).
- [x] Docs: `PROJECT_OVERVIEW.md`.

**Found during this sprint's own test run (not a live-verification bug like Sprint 17's):** `App.test.tsx` rendered `<App />` without the `<ToastProvider>` that `main.tsx` always wraps it in for real; that gap was invisible until `ExportCsvButton` (which calls `useToast()` unconditionally, not just on error) landed on a route the test visits. Fixed by wrapping the test's render in `ToastProvider`, matching the real composition instead of special-casing the new button.

### Sprint 19 — Email notifications

- [x] Backend: added `spring-boot-starter-mail` + `MailProperties` (`mols.mail.enabled`/`from`/`digest-cron`/`app-base-url`). SMTP connection itself rides Spring Boot's own `spring.mail.*` properties — `spring.mail.host` always has a default (`localhost`) so the auto-configured `JavaMailSender` bean exists regardless of whether a real server is set, and actual sending stays gated behind `mols.mail.enabled` (default `false`). Verified live: the app boots cleanly with no SMTP server configured, exactly the scenario this two-layer gating exists for.
- [x] `AlertDigestJob` (`@Scheduled(cron = "${mols.mail.digest-cron}")`, `@EnableScheduling` added to `LogisticsApplication`) reuses `DashboardService.lowStockAlerts()`/`staleOrderAlerts()` (made `public`) rather than duplicating that logic. Recipients: every enabled ADMIN with an email set (`AppUserRepository.findAllByRoleAndEnabledTrue`). Added a nullable `email` column to `app_users` (`V9__app_user_email.sql`, partial unique index) plus an inline-editable field on `UsersPage`/an optional field on `UserFormPage`, wired through `UserResponse`/`CreateUserRequest`/new `UpdateEmailRequest` and `PATCH /api/users/{id}/email`.
- [x] Self-service password reset: `POST /api/auth/forgot-password` (always 200, avoids account enumeration) and `POST /api/auth/reset-password`. The reset token is a JWT from `JwtService.generatePasswordResetToken`, carrying a `purpose` claim and a `resetPwdVersion` claim under a *different key* than the session token's `pwdVersion` — so `JwtAuthFilter` can never mistake it for a session credential (its own `extractPasswordVersion` reads only `pwdVersion`, finds nothing, and rejects it as revoked either way). Redeeming it calls the existing `AppUserAdminService.resetPassword`, which bumps `passwordVersion` — making the token single-use (a stale copy fails `isPasswordResetTokenValid`'s version check) and revoking any session issued under the old password, for free. Frontend: `ForgotPasswordPage`/`ResetPasswordPage`, a "Forgot your password?" link added next to Sprint 15's static hint on `LoginPage` (the hint stays as the fallback for accounts with no email on file).
- [x] Scope decision: fixed daily digest for v1, not per-user configurable — matches `mols.dashboard.*` already being one app-wide setting.
- [x] Tests: `JwtServiceTest` (reset-token issuance/validation/single-use/never-usable-as-a-session-token), `NotificationMailServiceTest` and `AlertDigestJobTest` (mocked `JavaMailSender`, the enabled/disabled gate, empty-alerts skip, email-required recipient filter), `AuthControllerTest` (both new endpoints, including the always-200 non-enumeration behavior and malformed/expired/reused-token rejection), plus frontend tests for `ForgotPasswordPage`/`ResetPasswordPage`. The actual SMTP send path was verified live via Mailpit (`docker-compose.yml`'s new `mailpit` service) — Testcontainers-based integration tests still don't run on this machine, so this was the only way to prove the full context boots and the mail wiring actually works end to end.
- [x] Docs: `PROJECT_OVERVIEW.md`, `.env.example` (new `MOLS_MAIL_*`/`SPRING_MAIL_*` vars), `docker-compose.yml` (optional `mailpit` service, off by default in behavior since `MOLS_MAIL_ENABLED` still defaults to `false`).

**Found and fixed during live verification (not by any test):** the persisted/browser-detected UI locale was only ever applied inside `AppLayout`, which doesn't mount until after authentication — so `LoginPage` and, now, the two new public reset pages always rendered in i18next's fallback language regardless of what the user had chosen, only switching to the saved locale after login. Pre-existing since Sprint 16, but Sprint 19 doubled the number of affected public pages, so fixed now: moved the `useLocale()` call up to `App.tsx` (kept in `AppLayout` too, for the switcher UI) so it applies at the app root before any route renders.

### Sprint 20 — Bulk CSV import

- [ ] Scope: Resources, Warehouses, Units only — the catalog data an organization bulk-loads once at onboarding, per the plan. Deliberately not Orders/Shipments/Stock: those carry relational dependencies (resource/warehouse ids, stock levels) that make a naive bulk import unsafe, so catalog-only sidesteps that class of problem for v1.
- [ ] Backend: `POST /api/{resources|warehouses|units}/import/preview` and `.../import/commit` (ADMIN-only, matching the existing create-endpoint role guard), accepting a CSV file (`multipart/form-data`). No CSV parsing library is in `pom.xml` yet (`opencsv` or `commons-csv`). Reuses each entity's existing field validation and the same case-insensitive duplicate-name check already backing `useDuplicateNameWarning`, rather than a parallel set of rules.
- [ ] Two-step preview-then-commit, not a single-shot upload: `preview` parses and validates without persisting, returning a row-by-row result (valid / duplicate-name warning / validation error, mirroring the messages `CreateXRequest` already produces) so the operator can review before committing anything; `commit` re-runs the same validation and persists. All-or-nothing per commit (one transaction) rather than partial-success — simpler to reason about than per-row rollback, and preview should already have caught anything that would fail commit.
- [ ] Frontend: an `ImportPage` per catalog module (or one page parameterized by resource, given the three are structurally similar) — file picker, a preview table color-coding row status, and a "Commit N valid rows" action gated on zero blocking errors (duplicate-name warnings stay advisory, like the existing single-record form warning, and shouldn't block commit).
- [ ] Tests: backend unit tests for parse/validate/preview (malformed CSV, missing required column, a row that trips existing field validation, a row that collides with an existing name) and the commit transaction boundary; a frontend test for the preview-table rendering given a mixed valid/invalid response.
- [ ] Docs: `PROJECT_OVERVIEW.md`; `HELP.md` (the expected CSV column format per entity, since operators prepare these files themselves).

---

## Nivel 2 — commercial ERP surface (recommended, not scheduled)

Not broken into sprints yet — these are larger, and several depend on decisions not yet made (which integrations, whether approval chains need to be configurable or fixed, etc.). Listed here so the option is documented rather than lost between conversations:

- Approval workflows (e.g., an order above a threshold needs sign-off before confirming).
- Barcode/QR scanning for warehouse floor operations (needs a camera-capable PWA flow or dedicated hardware).
- Custom/configurable reporting beyond the fixed Dashboard.
- Integrations with external systems (accounting/invoicing, e-commerce) via webhooks.
- A real public API product: API keys, rate limiting, and documentation aimed at third-party integrators (today's Swagger is internal documentation, not a product surface).
- Multi-currency support, if operating across countries with different currencies.

## Nivel 3 — multi-tenant SaaS infrastructure (only if selling to multiple orgs)

Only worth starting if the goal shifts from "run MOLS for one organization" to "sell MOLS as a product to many organizations from one deployment" — a multi-month/multi-team scope, not sprint-sized:

- Real multi-tenancy (data isolation per organization — today everything is one shared database).
- Billing/subscription management.
- Self-service onboarding (account creation without manual intervention).
- Horizontal scaling, backups/disaster recovery, an actual SLA.
- Compliance tooling (GDPR export/delete, SOC2-style audit controls).

---

## Verification per sprint

- Backend: `./mvnw.cmd test "-Dtest=!com.mls.logistics.integration.*"` locally (Testcontainers integration tests don't run on this machine — JDK 25/Windows NIO limitation, not a code issue; verify that suite via `docker compose up --build` instead).
- Frontend: `npm run lint`, `npm test`, `npx tsc -b --noEmit` inside `frontend/`.
- Any sprint touching UI: rebuild and run the real Docker stack, verify manually in the browser — passing tests alone isn't sufficient sign-off for this plan (this is exactly how the login-hint and duplicate-name-warning gaps in Sprint 15 were found in the first place: neither broke a test, both broke real usage).

**Last updated**: 2026-07-23 (Sprint 19 complete — low-stock/stale-order email digest, self-service password reset by email, and a pre-login locale fix found during live verification; Sprint 20 next)
