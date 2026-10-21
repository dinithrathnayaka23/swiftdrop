-- SwiftDrop baseline schema: users and roles only.
-- Each module adds its own tables in separate migrations named by member number,
-- e.g. V3_001__parcels.sql for Member 3. See docs/CONTRIBUTING.md.
-- Never edit a migration after it has been merged; add a new one instead.

CREATE TABLE roles (
    id   SMALLSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE
);

INSERT INTO roles (name) VALUES
    ('CUSTOMER'),
    ('MERCHANT'),
    ('COURIER'),
    ('DISPATCHER_ADMIN');

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),                         -- NULL for Google-only accounts
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(32),
    auth_provider VARCHAR(20)  NOT NULL DEFAULT 'LOCAL', -- LOCAL | GOOGLE
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

CREATE TABLE user_roles (
    user_id UUID     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);
