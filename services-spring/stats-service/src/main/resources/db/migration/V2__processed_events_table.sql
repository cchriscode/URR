-- H7: Deduplication table to prevent double-counting on Kafka redelivery
CREATE TABLE IF NOT EXISTS processed_events (
    event_key VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events(processed_at);
