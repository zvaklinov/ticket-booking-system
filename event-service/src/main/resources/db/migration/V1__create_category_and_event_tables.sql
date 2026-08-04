CREATE TABLE category (
    id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_category_name UNIQUE (name)
);

CREATE TABLE event (
    id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title    VARCHAR(255) NOT NULL,
    description TEXT,
    category_id UUID NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    location VARCHAR(255),
    venue_name VARCHAR(255),
    venue_timezone VARCHAR(255),
    start_time_utc TIMESTAMPTZ NOT NULL,
    end_time_utc TIMESTAMPTZ NOT NULL,
    booking_opens_at_utc TIMESTAMPTZ NOT NULL,
    booking_closes_at_utc TIMESTAMPTZ NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),
    status VARCHAR(50) NOT NULL
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INT NOT NULL DEFAULT 1
);

CREATE TABLE event_price_tier (
    id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),

    CONSTRAINT uq_event_price_tier UNIQUE (event_id, price, currency)
);

CREATE INDEX idx_event_status_start_time ON event (status, start_time_utc);
CREATE INDEX idx_event_category_start_time ON event (category_id, start_time_utc);
CREATE INDEX idx_event_location_start_time ON event (location, start_time_utc);
CREATE INDEX idx_event_start_time ON event (start_time_utc);
CREATE INDEX idx_event_price_tier_price ON event_price_tier (price, event_id);