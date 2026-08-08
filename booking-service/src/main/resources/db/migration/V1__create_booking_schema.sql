
CREATE TABLE event_bookability (
    event_id                UUID PRIMARY KEY,
    status                   VARCHAR(50) NOT NULL
        CHECK (status IN ('PUBLISHED', 'CANCELLED', 'ARCHIVED')),
    booking_opens_at_utc     TIMESTAMPTZ NOT NULL,
    booking_closes_at_utc    TIMESTAMPTZ NOT NULL,
    start_time_utc           TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- idempotency_key: backs POST /seat-holds only for now. Scoped per user, not global.
CREATE TABLE idempotency_key (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL,
    idempotency_key   VARCHAR(255) NOT NULL,
    request_hash      VARCHAR(64) NOT NULL,
    resource_id       UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_idempotency_key UNIQUE (user_id, idempotency_key)
);

CREATE TABLE seat (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id          UUID NOT NULL,
    seat_label        VARCHAR(50) NOT NULL,
    price             NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    currency          VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),
    status            VARCHAR(20) NOT NULL
        CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED', 'UNAVAILABLE')),
    active_hold_id    UUID,
    hold_expires_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           INT NOT NULL DEFAULT 1,

    CONSTRAINT uq_seat_event_label UNIQUE (event_id, seat_label)
);

CREATE INDEX idx_seat_event_status ON seat (event_id, status);

CREATE TABLE seat_hold (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id             UUID NOT NULL,
    user_id              UUID NOT NULL,
    status               VARCHAR(20) NOT NULL
        CHECK (status IN ('ACTIVE', 'PAYMENT_PENDING', 'CONFIRMED', 'EXPIRED', 'RELEASED')),
    expires_at           TIMESTAMPTZ NOT NULL,
    payment_deadline_at  TIMESTAMPTZ,
    confirmed_at         TIMESTAMPTZ,
    released_at          TIMESTAMPTZ,
    total_amount         NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0),
    currency             VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              INT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uq_active_hold_per_user_event
    ON seat_hold (user_id, event_id)
    WHERE status IN ('ACTIVE', 'PAYMENT_PENDING');

CREATE INDEX idx_seat_hold_active_expiry
    ON seat_hold (expires_at)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_seat_hold_payment_pending_deadline
    ON seat_hold (payment_deadline_at)
    WHERE status = 'PAYMENT_PENDING';

CREATE TABLE seat_hold_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seat_hold_id  UUID NOT NULL REFERENCES seat_hold(id) ON DELETE CASCADE,
    seat_id       UUID NOT NULL REFERENCES seat(id) ON DELETE RESTRICT,
    price         NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    currency      VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),

    CONSTRAINT uq_seat_hold_item UNIQUE (seat_hold_id, seat_id)
);

CREATE TABLE booking (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_hold_id              UUID NOT NULL UNIQUE REFERENCES seat_hold(id),
    event_id                    UUID NOT NULL,
    user_id                     UUID NOT NULL,
    status                      VARCHAR(30) NOT NULL
        CHECK (status IN ('CONFIRMED', 'CANCELLATION_PENDING', 'CANCELLED')),
    total_amount                NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0),
    currency                    VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),
    confirmed_at                TIMESTAMPTZ NOT NULL,
    cancellation_requested_at   TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                     INT NOT NULL DEFAULT 1
);

CREATE INDEX idx_booking_user ON booking (user_id, created_at);
CREATE INDEX idx_booking_event ON booking (event_id, status);

CREATE TABLE booking_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID NOT NULL REFERENCES booking(id) ON DELETE CASCADE,
    seat_id     UUID NOT NULL REFERENCES seat(id) ON DELETE RESTRICT,
    price       NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    currency    VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),

    CONSTRAINT uq_booking_item UNIQUE (booking_id, seat_id)
);