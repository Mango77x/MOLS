-- =====================================================================
-- MOLS V8 — Track when a user's password was last changed
--
-- JwtAuthFilter previously validated a token by username + expiration
-- only, so disabling a user or resetting their password didn't revoke
-- any JWT already issued to them — it kept working for up to
-- SECURITY_JWT_EXPIRATION_MS (24h by default). password_changed_at lets
-- the filter reject a token whose issued-at predates the user's most
-- recent password change (which AppUserAdminService now sets on both
-- user creation and password reset).
-- =====================================================================

ALTER TABLE app_users ADD COLUMN password_changed_at TIMESTAMP;

-- Backfill existing users: treat "now" as the last known password change,
-- since we have no earlier record of it. This only affects tokens already
-- in flight at deploy time, which is an acceptable one-off cost.
--
-- Truncated to the second: a JWT's `iat` claim is whole-seconds precision
-- (JWT NumericDate), so a sub-second timestamp here could make a token
-- issued in the very same second as this migration — but genuinely after
-- it — look like it predates it once `iat`'s fractional part is lost,
-- and JwtAuthFilter would wrongly treat that token as revoked.
UPDATE app_users SET password_changed_at = date_trunc('second', CURRENT_TIMESTAMP)
WHERE password_changed_at IS NULL;

ALTER TABLE app_users ALTER COLUMN password_changed_at SET NOT NULL;
