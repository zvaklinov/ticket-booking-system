ALTER TABLE payment ADD COLUMN refund_submitted_at TIMESTAMPTZ;

CREATE INDEX idx_payment_refund_pending
    ON payment (refund_submitted_at, updated_at)
    WHERE status = 'REFUND_PENDING';