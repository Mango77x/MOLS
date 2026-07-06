# MOLS — Multimodal Operative Logistics System

<p align="center">
  <img src="https://github.com/user-attachments/assets/6c3cae38-df16-4c85-a334-6a53cb1f27fa" width="300" alt="MOLS logo">
</p>

<p align="center">
  <a href="https://github.com/Mango420x/MOLS/actions/workflows/ci.yml">
    <img alt="BUILD" src="https://img.shields.io/github/actions/workflow/status/Mango420x/MOLS/ci.yml?branch=main&label=build&style=for-the-badge">
  </a>
  <a href="HELP.md">
    <img alt="HELP" src="https://img.shields.io/badge/help-HELP.md-informational?style=for-the-badge">
  </a>
  <a href="PROJECT_OVERVIEW.md">
    <img alt="PROJECT OVERVIEW" src="https://img.shields.io/badge/docs-PROJECT_OVERVIEW-informational?style=for-the-badge">
  </a>
</p>

MOLS is a logistics app to manage warehouses, resources, stock, orders and shipments, with an audit log (movements) so you can trace what changed and why.

---

## Highlights

- **Append-only audit trail**: every stock change automatically records a Movement
  (`ENTRY` / `EXIT`) with the acting user; movements can never be edited or deleted
- **Status state machines**: order and shipment lifecycles are enforced enums
  (e.g. a `DELIVERED` shipment or `COMPLETED` order can never be reverted)
- **Concurrency-safe stock**: optimistic locking detects simultaneous adjustments
  (HTTP 409) instead of silently losing updates
- **Database-level integrity**: CHECK/UNIQUE/NOT NULL constraints and FK indexes
  managed by Flyway migrations
- **Hardened login**: temporary lockout after repeated failures, security event
  logging, 12+ character password policy
- Orders with items (create/edit, inline item management)
- Shipments with fulfillment on delivery
- Traceability views (Order/Shipment details show linked movements)
- `/ui` dashboard with KPIs, charts (Chart.js), recent activity and alerts
- First-run setup to create the initial admin user
- Admin-only user management (roles, password reset, enable/disable)

---

<details>
<summary>Resumen en español (🇪🇸)</summary>

MOLS es un sistema web para gestionar stock, pedidos y envíos, con auditoría de movimientos para trazabilidad.

Para detalles técnicos y arquitectura, consulta [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md).
</details>

---

## Tech Stack

- Java 21, Spring Boot 4 (Spring MVC)
- PostgreSQL + Spring Data JPA (Hibernate, `validate` mode)
- Flyway (versioned database migrations)
- Testcontainers (end-to-end integration tests on a real PostgreSQL)
- CI: GitHub Actions with JaCoCo coverage gate, CodeQL, OWASP Dependency-Check, CycloneDX SBOM and Dependabot
- Spring Security (JWT for API — header or HttpOnly cookie — + session login for UI)
- React 19 + TypeScript + Vite + Tailwind 4 (new SPA at `/app`, incremental migration)
- Thymeleaf + Bootstrap 5.3 (current admin UI at `/ui`)
- OpenAPI/Swagger (springdoc)
- Docker + Docker Compose

---

## Links

With the application running:

- UI dashboard: http://localhost:8080/ui
- New React interface (incremental migration): http://localhost:8080/app
- First-run setup: http://localhost:8080/ui/setup
- Swagger UI: http://localhost:8080/swagger-ui.html

---

## Dashboard (UI)

The dashboard at `/ui` shows sensible empty states on a fresh database, and becomes more useful as you build history.

Includes:

- KPI cards: total orders (with pending), stock quantity across warehouses, active shipments, low-stock alerts, recent movements (24h), fulfillment rate
- Charts: stock distribution by warehouse (bar), movements by type (doughnut), orders by status (pie)
- Alerts: low stock items (action link to adjust), stale pending orders (link to order detail)

Dashboard thresholds live in `src/main/resources/application.properties`:

- `mols.dashboard.low-stock-threshold` (default: 10)
- `mols.dashboard.critical-stock-threshold` (default: 5)
- `mols.dashboard.stale-order-days` (default: 3)
- `mols.dashboard.recent-activity-hours` (default: 24)
- `mols.dashboard.movement-chart-days` (default: 30)
- `mols.dashboard.fulfillment-target-percent` (default: 90)

---

## Docs

- Technical details: [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)
- Local run / troubleshooting: [HELP.md](HELP.md)

---

## Run (quick)

```powershell
./mvnw.cmd spring-boot:run
```

Or with Docker:

```powershell
docker compose up --build -d
```

---

## License

This project is for educational and portfolio purposes.

---

## Contributing

PRs are welcome.

- Keep changes small and focused
- Add/adjust tests when behavior changes
- Prefer service-layer rules (controllers stay thin)
