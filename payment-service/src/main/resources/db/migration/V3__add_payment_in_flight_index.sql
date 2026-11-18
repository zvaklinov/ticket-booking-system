CREATE INDEX idx_payment_in_flight
    ON payment (created_at)
    WHERE status IN ('CREATED', 'REQUIRES_PAYMENT_METHOD', 'REQUIRES_ACTION', 'PROCESSING');