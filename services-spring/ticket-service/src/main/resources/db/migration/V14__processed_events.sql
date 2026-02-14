-- Processed events table for consumer idempotency (prevents duplicate event processing)
CREATE TABLE IF NOT EXISTS processed_events (
    event_key VARCHAR(255) PRIMARY KEY,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processed_events_consumer ON processed_events(consumer_group, processed_at);
