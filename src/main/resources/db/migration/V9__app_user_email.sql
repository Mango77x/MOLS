-- =====================================================================
-- MOLS V9 — Optional email address on app_users
--
-- Needed for Sprint 19: the low-stock/stale-order digest job emails
-- every enabled ADMIN who has one set, and the self-service password
-- reset flow looks a user up by email. Nullable — existing accounts
-- (including the first-run ADMIN) have none until an admin sets one via
-- the Users page, and the app must not force a backfill to keep working.
-- =====================================================================

ALTER TABLE app_users ADD COLUMN email VARCHAR(255);

-- A plain UNIQUE constraint would already allow any number of NULL rows in
-- Postgres, so this partial index is only about intent: it's the email
-- itself that must be unique, and it guarantees findByEmail() can never
-- hit NonUniqueResultException once two accounts have one set.
CREATE UNIQUE INDEX idx_app_users_email ON app_users (email) WHERE email IS NOT NULL;
