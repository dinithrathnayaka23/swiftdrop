-- Email ownership: an account may sign in only once its email address is verified (link sent by
-- email, a Google sign-in for that address, or a completed password reset). Until then the
-- address is only a claim, and Google sign-in will not link to the account.

ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

COMMENT ON COLUMN users.email_verified_at IS 'When the user proved they own the email address; NULL = not verified yet';

-- Accounts created before verification existed keep working.
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;
