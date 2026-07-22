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

#### Sprint 17 — i18n: remaining pages + backend error codes + ES/FR content

- [ ] Extract the remaining page-specific strings: the 9 list/form page pairs (Warehouses, Resources, Vehicles, Units, Stock, Orders, Shipments, Movements, Users) — filters, column headers, field labels/hints, wizard step copy.
- [ ] Backend: add `code` (+ `params` where the message interpolates a value) to the custom exceptions and `GlobalExceptionHandler`'s own three messages, scoped to the error codes that actually surface in normal use (confirmed live during the audit: insufficient stock on reservation, duplicate resource, the delete-integrity guards, "can't disable the last ADMIN", field validation). The long tail of the ~67 messages keeps falling back to English via `message` until migrated opportunistically later.
- [ ] Frontend: extend `ApiError`/`extractApiError` to carry `code`/`params`; translate via `t(code, params)` when the code is recognized in `errors.json`, otherwise show `message` unchanged.
- [ ] Write full Spanish and French content for everything extracted across Sprint 16 and 17.
- [ ] Tests: the code-based `extractApiError` path (recognized code → translated; unknown code → English fallback), plus spot coverage on a couple of migrated pages.
- [ ] Docs: `PROJECT_OVERVIEW.md`, plus a short mention in `HELP.md` if the language switcher needs explaining to end users/operators.

### Sprint 18 — CSV/Excel export

- [ ] Export action on `DataTable`-backed list pages, hitting the existing paginated endpoints without the page/size cap (or a dedicated `?export=true` full-result path) and generating a CSV client-side or via a small backend endpoint per resource.
- [ ] Scope: start with the pages an operator actually needs for reporting/backup — Stock, Movements, Orders — rather than all nine at once; extend to the rest once the pattern is proven.

### Sprint 19 — Email notifications

- [ ] Backend: a scheduled job re-using `DashboardService`'s existing low-stock/stale-order computation, plus an email channel (SMTP config + a minimal template).
- [ ] Trigger points: low stock crossing the existing threshold, an order stale past the existing `staleOrderDays` threshold, and (ties into Sprint 15's login-hint fix) a proper password-reset email flow replacing the current "contact your administrator" static hint.
- [ ] Scope check before starting: confirm whether this needs to be per-user configurable (which alerts, how often) or a single fixed policy for v1 — fixed policy first, configurability only if requested.

### Sprint 20 — Bulk CSV import

- [ ] Import flow for Resources/Warehouses/Units (the catalog data an organization needs to bulk-load once at onboarding) — CSV upload, validation preview before commit, reuse of the existing duplicate-name check and field validation rather than a parallel set of rules.

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

**Last updated**: 2026-07-22 (Sprint 16 complete — i18n infrastructure and the shared-UI-surface translation pass (EN/ES/FR); Sprint 17 next)
