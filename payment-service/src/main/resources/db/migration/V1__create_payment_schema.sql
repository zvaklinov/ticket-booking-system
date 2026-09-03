CREATE TABLE payment (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hold_id                   UUID NOT NULL,
    user_id                   UUID NOT NULL,
    amount                    NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    currency                  VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),
    status                    VARCHAR(30) NOT NULL
        CHECK (status IN ('CREATED', 'REQUIRES_PAYMENT_METHOD', 'REQUIRES_ACTION',
                          'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED',
                          'REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED')),
    stripe_payment_intent_id  VARCHAR(255),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at              TIMESTAMPTZ,
    failed_at                 TIMESTAMPTZ,
    refunded_at               TIMESTAMPTZ,
    refund_attempt_count      INT NOT NULL DEFAULT 0,
    version                   INT NOT NULL DEFAULT 1
);

-- A hold may have at most one live payment attempt.
CREATE UNIQUE INDEX uq_payment_live_per_hold
    ON payment (hold_id)
    WHERE status NOT IN ('FAILED', 'CANCELLED');

CREATE INDEX idx_payment_user ON payment (user_id, created_at);
CREATE UNIQUE INDEX uq_payment_intent ON payment (stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;

CREATE TABLE payment_attempt (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id                UUID NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    attempt_number            INT NOT NULL,
    stripe_payment_intent_id  VARCHAR(255),
    status                    VARCHAR(50) NOT NULL,
    failure_code              VARCHAR(100),
    failure_message           TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at              TIMESTAMPTZ,

    CONSTRAINT uq_payment_attempt_number UNIQUE (payment_id, attempt_number)
);

CREATE TABLE stripe_webhook_event (
    stripe_event_id    VARCHAR(255) PRIMARY KEY,
    event_type         VARCHAR(100) NOT NULL,
    payment_intent_id  VARCHAR(255),
    payload            JSONB NOT NULL,
    processing_status  VARCHAR(20) NOT NULL
        CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED')),
    received_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at       TIMESTAMPTZ,
    error_message      TEXT,
    attempt_count      INT NOT NULL DEFAULT 0
);

CREATE TABLE idempotency_key (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL,
    request_hash     VARCHAR(64) NOT NULL,
    resource_id      UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_idempotency_key UNIQUE (user_id, idempotency_key)
);

CREATE TABLE processed_message (
    message_id    UUID PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempt_count  INT NOT NULL DEFAULT 0,
    last_error     TEXT
);

CREATE INDEX idx_outbox_unpublished
    ON outbox (created_at)
    WHERE published_at IS NULL;