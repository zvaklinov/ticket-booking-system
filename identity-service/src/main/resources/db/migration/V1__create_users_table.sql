CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(60) NOT NULL,
    role           VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        INT NOT NULL DEFAULT 1,

    CONSTRAINT uq_users_email UNIQUE (email)
);