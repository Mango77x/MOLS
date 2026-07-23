# MOLS — Future Ideas

This is a backlog of options, not a roadmap. Nothing here is scheduled, and nothing here is a commitment — these are ideas worth remembering rather than losing between conversations. An idea only "graduates" into `docs/DEVELOPMENT_PLAN.md` (with a sprint number, a concrete design, and a verification plan) the day it's actually picked up.

Entries are grouped by theme, not priority. Each one notes what it is, why it would matter, a rough size (S/M/L), and any decision that has to be made before it could even be scoped.

---

## Nivel 2 — Commercial ERP surface

Moved here from `DEVELOPMENT_PLAN.md` (previously listed as "recommended, not scheduled"). These take MOLS from "solid internal tool" toward "software a company could run as its system of record" — real scope increases, not polish.

- **Approval workflows** — an order above a configurable threshold needs sign-off before it can be confirmed/shipped. *Why*: table stakes for any org with a finance/procurement function. *Size*: M. *Needs deciding first*: fixed single-approver vs. configurable chains — the latter is a much bigger design (roles, delegation, escalation).
- **Barcode/QR scanning** — scan a resource/warehouse code instead of picking it from a dropdown during stock adjustments and shipments. *Why*: the realistic gap between "a web form" and "something a warehouse floor worker would actually use." *Size*: M. *Needs deciding first*: camera-based PWA scanning (works today, no new hardware) vs. dedicated scanner hardware integration (bigger, and nobody's asked for it).
- **Custom/configurable reporting** — let a user build a report (pick dimensions/filters/date range) beyond the fixed Dashboard. *Why*: the Dashboard answers the questions its designer anticipated; real operations teams eventually ask a question it didn't. *Size*: L — this is close to a small BI tool.
- **External integrations via webhooks** — notify an external system (accounting, e-commerce) when an order/shipment changes state. *Why*: MOLS today is an island; every real deployment eventually needs to talk to something else. *Size*: M. *Needs deciding first*: which system, since "generic webhooks" without a real consumer to test against tends to ship half-validated.
- **A real public API product** — API keys, rate limiting, and documentation aimed at third-party integrators. Today's Swagger is internal documentation (gated behind `SPRINGDOC_ENABLED`), not a product surface. *Why*: turns MOLS from "an app" into "a platform." *Size*: L. *Needs deciding first*: this only makes sense if there's an actual third party who'd consume it — otherwise it's speculative surface area with no real usage to validate it against.
- **Multi-currency support** — resources/orders priced and reported in more than one currency. *Why*: relevant the moment operations cross a currency border. *Size*: M — touches persistence (store amounts + currency, not just numbers), display (locale-aware formatting, already partly solved by the i18n work), and reporting (conversion at what rate, and when).

---

## Nivel 3 — Multi-tenant SaaS infrastructure

Moved here from `DEVELOPMENT_PLAN.md`. Only worth starting if the goal shifts from "run MOLS for one organization" to "sell MOLS as a product to many organizations from one deployment" — a multi-month/multi-team scope, not sprint-sized. Listed for completeness, not because any of it is close to being picked up.

- **Real multi-tenancy** — data isolation per organization; today everything lives in one shared database/schema. *Size*: L (foundational — almost everything downstream depends on this decision being made first: schema-per-tenant vs. row-level isolation vs. separate databases).
- **Billing/subscription management** — plans, invoicing, payment provider integration. *Size*: M, but only meaningful once multi-tenancy exists.
- **Self-service onboarding** — an organization can sign up and get a working instance without a human doing it manually. *Size*: M.
- **Horizontal scaling, backups/disaster recovery, an actual SLA** — operational maturity for running someone else's business, not just a demo. *Size*: L, ongoing rather than a one-time project.
- **Compliance tooling** — GDPR export/delete, SOC2-style audit controls. *Size*: M, and largely driven by whichever customer/market actually requires it first.

---

## Portfolio visibility

Not product features — ways to make the engineering work already done easier for someone to evaluate quickly. Given MOLS's purpose is a portfolio piece, these plausibly matter more per hour spent than another Nivel 2 item.

- **A short demo video/GIF** embedded in the README — a 30-60s walkthrough (login → dashboard → an order moving through its lifecycle → the audit trail proving it) for anyone who won't click through to the live demo. *Size*: S.
- **An architecture diagram** — one picture of the request path (React SPA → Spring Boot → Postgres, JWT/session handling, the audit-trail write path) for someone skimming in 30 seconds. *Size*: S.
- **A write-up of the audit-driven roadmap process** — the actual story behind `DEVELOPMENT_PLAN.md`: a live product/code audit found concrete gaps, gaps got tiered by how big a bet each was, execution happened one verified sprint at a time. This is a better "tell me about a time you prioritized technical debt" answer than the code alone. *Size*: S — the material already exists in `DEVELOPMENT_PLAN.md`'s history, this is mostly editing, not new work.
