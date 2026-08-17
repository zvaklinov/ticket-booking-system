CREATE TABLE processed_message (
    message_id    UUID PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- TODO Phase 8: this table grows without bound. It needs a retention policy —
-- deleting rows older than the topic's retention period is safe, since a message
-- older than that can no longer be redelivered.