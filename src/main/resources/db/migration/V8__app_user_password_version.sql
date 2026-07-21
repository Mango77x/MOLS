-- =====================================================================
-- MOLS V8 — Track a user's password version, for JWT revocation
--
-- JwtAuthFilter previously validated a token by username + expiration
-- only, so disabling a user or resetting their password didn't revoke
-- any JWT already issued to them — it kept working for up to
-- SECURITY_JWT_EXPIRATION_MS (24h by default). password_version lets the
-- filter reject a token whose embedded version no longer matches the
-- user's current one (AppUserAdminService bumps it on every reset).
--
-- An incrementing integer rather than a "changed at" timestamp: a
-- timestamp can only be compared at whatever precision it's embedded in
-- the token at (a JWT's `iat` is whole-seconds), so two password-set
-- events within the same second would be indistinguishable — an
-- integer bump can't collide like that no matter how fast the events
-- happen.
-- =====================================================================

ALTER TABLE app_users ADD COLUMN password_version INT NOT NULL DEFAULT 0;
