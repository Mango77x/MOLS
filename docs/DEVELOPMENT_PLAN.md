# MOLS — Development Plan: Technical Debt & Product Completion

## Context

Two audits were run against the app: a product/UX audit (manual, hands-on testing of every module) and a technical code review (backend architecture, security, data/performance, frontend, test coverage). This plan tracks the resulting work, split into two phases:

1. **Phase 1 — Technical debt**: real bugs and gaps confirmed by reading the actual code (not just observed behavior — two product-audit findings, "empty required field submits silently" and "failed delete shows no feedback", turned out to be false positives from the browser-automation tool used for testing, not real bugs, and are intentionally excluded here).
2. **Phase 2 — Product completion**: the UX/product gaps identified in the audit, prioritized by impact vs. effort.

Same workflow as prior sprints: one `sprint-N` branch per sprint, opened as a PR against `main`, merged by the maintainer. This plan doc is checked off sprint by sprint, same as `PROJECT_OVERVIEW.md`'s "Last updated" line.

---

## Phase 1 — Technical debt

### Sprint 8 — Data integrity (critical)

- [x] Remove `CascadeType.REMOVE` from `Order.shipments`/`Unit.orders`/`Vehicle.shipments`/`Warehouse.stockItems`/`Resource.stocks` (was bypassing the delete guards already enforced on `Order`/`Shipment`/`Stock` directly, letting a cascade delete remove `COMPLETED` orders / `DELIVERED` shipments / audited stock and orphan `Movement` rows). `Order.items` and `Shipment.items` keep their cascade — those are genuinely owned line items, already guarded before the cascade fires.
- [x] Add explicit delete guards to `UnitService`, `VehicleService`, `WarehouseService`, `ResourceService`, mirroring `OrderService.deleteOrder`'s pattern (new `existsBy*` queries on `OrderRepository`/`ShipmentRepository`/`StockRepository`/`OrderItemRepository`).
- [x] Extend `OrderService.deleteOrder`: reject any order with a `DELIVERED` shipment (not just `COMPLETED` orders), and reject any order that still has *any* shipment at all, since shipments no longer cascade-delete with their order.
- [x] `V7__backfill_shipment_items.sql` — backfill `shipment_items` for shipments delivered before V6, fixing `deliveredQuantity` showing 0 on old completed orders. Verified live: orders #1-#3 now show `deliveredQuantity` matching `quantity` instead of 0.
- [x] Unit tests for all five delete guards. No automated migration-compatibility test was added for V7 — a fresh Testcontainers database runs V1-V7 in sequence with no historical data for V7 to backfill, so the scenario doesn't naturally arise in that flow; the fix was instead verified directly against the real, pre-existing seeded database: `deliveredQuantity` for orders #1-#3 went from 0 to matching their full `quantity` after the migration ran, and all five new delete guards were confirmed live (clean 400s, nothing actually deleted).

### Sprint 9 — Backend correctness & security

- [x] `StockService.createStock` — guards against the `UNIQUE(resource_id, warehouse_id)` constraint before saving (new `DuplicateResourceException` → 409) instead of a raw 500.
- [x] JWT revocation: `JwtAuthFilter` now rejects a token if the user is disabled.
- [x] JWT revocation: rejects a token whose embedded `pwdVersion` claim no longer matches the user's current `app_users.password_version` (`V8` migration, integer counter starting at 0, bumped on every reset).
  - This went through three iterations before landing correctly, worth recording for anyone touching this code later:
    1. First attempt compared the token's `iat` (issued-at) against a `passwordChangedAt` timestamp. Broke a brand-new user's very first login when it happened in the same wall-clock second as account creation, because a JWT's `iat` is whole-seconds precision and truncation made the token look older than it was.
    2. Second attempt truncated both sides to seconds and switched to an equality check instead of before/after. Passed locally, but CI caught it: truncating login and a same-second reset to the same value made them compare equal, so the reset silently failed to revoke anything.
    3. Final design: an incrementing integer (`passwordVersion`) instead of any timestamp. Two password-set events can never produce the same version number no matter how close together they happen, so there's no precision to lose and no ordering ambiguity — closes the whole bug class rather than another edge case of it. Stress-tested locally with zero delay between create → login → reset → check, including two resets back to back.
- [x] Fixed stale Swagger docs claiming OPERATOR is read-only.
- [x] Removed dead code: 6 unused `createX(Entity)` service overloads + unused `UpdateStockRequest` DTO.
- [x] `CreateUserRequest.role`/`UpdateRoleRequest.role` now use the same `.from(String)` friendly-error pattern as every other enum in the app (`Role.from`, new).
- [x] Bonus (found while touching this code, not originally itemized): `AppUserAdminService` was enforcing a stale, unreachable 6-character password minimum instead of the 12 enforced everywhere else — fixed to a shared `MIN_PASSWORD_LENGTH` constant.

### Sprint 10 — Frontend bug fixes

- [x] `SelectField` missing `defaultValue=""`. `OrderItemsManager` already had it correctly — the real instances were `ResourceFormPage` (criticality), `VehicleFormPage` (type, status), plus two not originally itemized but found by grepping every `<SelectField>` in the app: `StockAdjustFormPage` (operation) and `UserFormPage` (role). The last one was the most consequential — creating a user without touching the Role field silently defaulted to **ADMIN**.
- [x] `OrderEditFormPage`/`OrderItemsManager` now lock when the order is `COMPLETED`/`CANCELLED`: the status `<select>` is disabled with an explanatory note, and `OrderItemsManager` (extended with a `locked` prop) hides the add form and the per-row Update/Remove actions, leaving a read-only items table — mirrors `ShipmentFormPage`'s `itemsLocked` pattern.
- [x] Shared enum-label helper (`src/lib/enumLabels.ts`) for `OrderStatus`/`ShipmentStatus`/`VehicleStatus`/`VehicleType`, applied to every filter dropdown, status badge, and form select that was showing raw enum values or hardcoding its own label copy — including the vehicle-picker id inconsistency between the order wizard and shipment edit page.
- [x] Catch-all 404 route (`NotFoundPage`) plus two redirect aliases for the specific guessable URLs found in the product audit: `/app/stock` → `/app/stocks`, `/app/audit-log` → `/app/movements`.
- [x] Custom `ConfirmDialog` (styled, `alertdialog` role, closes on Escape/backdrop click) replacing every `window.confirm()` call in the app: row deletes (`RowActions`), order-item removal (`OrderItemsManager`), and the two confirmations on the Users page (role change, enable/disable).

### Sprint 11 — Frontend test infrastructure

- [x] Added `@testing-library/react`, `@testing-library/user-event`, `jsdom` and switched `test.environment` to `jsdom` in `vite.config.ts`, with a `src/test/setup.ts` handling auto-cleanup between tests (needed explicitly since this project doesn't use Vitest's `globals: true`) and a `matchMedia` stub (unimplemented in jsdom, needed by `useTheme`).
- [x] Component/behavior tests for every Sprint 10 fix: `enumLabels` completeness, `UserFormPage` role-select validation (the ADMIN-by-default bug), `OrderEditFormPage` terminal-state lock, `RowActions`/`DeleteAction` confirm-dialog flow including the failed-delete-surfaces-a-toast regression guard, `ConfirmDialog` itself (open/close/confirm/cancel/Escape/backdrop-click), and `App` routing (404 catch-all, `/stock` and `/audit-log` redirects).
- [x] Test for the enum-label helper.
- Went from 3 files / 8 pure-function tests to 9 files / 35 tests, now covering DOM rendering, form validation, async user interaction, and routing.

---

## Phase 2 — Product completion

### Sprint 12 — Feedback consistency & mobile tables
- [ ] Audit all forms for consistent `FormBanner`/`FieldError`/toast usage.
- [ ] Card view for data tables on mobile viewports (replaces the hidden horizontal-scroll actions column).

### Sprint 13 — Dashboard polish
- [ ] "Recent activity" widget shows resource/warehouse names instead of raw stock IDs.
- [ ] Static legend on the donut charts (today hover-only).

### Sprint 14 (stretch)
- [ ] Soft duplicate-name warning on Warehouses/Resources/Units.
- [ ] Minor copy fixes ("1 results" → "1 result", etc.)

---

**Last updated**: 2026-07-21 (Sprint 11 complete)
