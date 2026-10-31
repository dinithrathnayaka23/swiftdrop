-- SwiftDrop has exactly one admin: the account configured by ADMIN_EMAIL / ADMIN_PASSWORD
-- (synced on every startup by AdminAccountInitializer). This index makes a second one impossible.
CREATE UNIQUE INDEX ux_users_single_admin ON users (role) WHERE role = 'DISPATCHER_ADMIN';
