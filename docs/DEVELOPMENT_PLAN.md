# MOLS — Development Plan: Technical Debt & Product Completion

## Context

Two audits were run against the app: a product/UX audit (manual, hands-on testing of every module) and a technical code review (backend architecture, security, data/performance, frontend, test coverage). This plan tracks the resulting work, split into two phases:

1. **Phase 1 — Technical debt**: real bugs and gaps confirmed by reading the actual code (not just observed behavior — two product-audit findings, "empty required field submits silently" and "failed delete shows no feedback", turned out to be false positives from the browser-automation tool used for testing, not real bugs, and are intentionally excluded here).
2. **Phase 2 — Product completion**: the UX/product gaps identified in the audit, prioritized by impact vs. effort.

Same workflow as prior sprints: one `sprint-N` branch per sprint, opened as a PR against `main`, merged by the maintainer. This plan doc is checked off sprint by sprint, same as `PROJECT_OVERVIEW.md`'s "Last updated" line.

---

## Phase 1 — Technical debt

### Sprint 8 — Data integrity (critical)

- [ ] Remove `CascadeType.REMOVE` from `Order`/`Unit`/`Vehicle`/`Warehouse`/`Resource` `@OneToMany` relations (was bypassing the delete guards already enforced on `Order`/`Shipment` directly, letting a cascade delete remove `COMPLETED` orders / `DELIVERED` shipments and orphan `Movement` audit rows).
- [ ] Add explicit delete guards to `UnitService`, `VehicleService`, `WarehouseService`, `ResourceService`, mirroring `OrderService.deleteOrder`'s pattern.
- [ ] Extend `OrderService.deleteOrder` to also reject `PARTIALLY_SHIPPED` orders that have a `DELIVERED` shipment (today only `COMPLETED` is blocked).
- [ ] `V7__backfill_shipment_items.sql` — backfill `shipment_items` for shipments delivered before V6, fixing `deliveredQuantity` showing 0 on old completed orders.
- [ ] Unit + integration tests for both fixes, including a migration-compatibility test.

### Sprint 9 — Backend correctness & security

- [ ] `StockService.createStock` — guard against the `UNIQUE(resource_id, warehouse_id)` constraint before saving, returning 409 instead of a raw 500.
- [ ] JWT revocation: reject a token if the user is disabled (`JwtAuthFilter` currently never checks `isEnabled()`).
- [ ] JWT revocation: reject a token issued before the user's last password change (`app_users.password_changed_at`, new migration).
- [ ] Fix stale Swagger docs claiming OPERATOR is read-only.
- [ ] Remove dead code: 6 unused `createX(Entity)` service overloads + unused `UpdateStockRequest` DTO.
- [ ] Align `CreateUserRequest.role`/`UpdateRoleRequest.role` validation with the `.from(String)` friendly-error pattern used by every other enum in the app.

### Sprint 10 — Frontend bug fixes

- [ ] `SelectField` missing `defaultValue=""` in `ResourceFormPage`, `VehicleFormPage`, `OrderItemsManager` — required-select validation silently never fires.
- [ ] Lock `OrderEditFormPage`/`OrderItemsManager` controls when the order is `COMPLETED`/`CANCELLED` (mirror `ShipmentFormPage`'s `itemsLocked` pattern).
- [ ] Shared enum-label helper, replacing hardcoded/duplicated label lists across filter dropdowns and forms.
- [ ] Catch-all 404 route (today an unmatched path like `/app/stock` renders a blank page).
- [ ] Custom delete-confirmation modal replacing native `window.confirm()`.

### Sprint 11 — Frontend test infrastructure

- [ ] Add `@testing-library/react` + jsdom environment to the Vitest config (today: 3 files, 8 tests, all pure-function, no DOM).
- [ ] Component/behavior tests for the Sprint 10 fixes.
- [ ] Test for the enum-label helper.

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

**Last updated**: 2026-07-21 (Sprint 8 kickoff)
