-- Add idempotency key to prevent duplicate reservations from network retries
ALTER TABLE reservations ADD COLUMN idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX idx_reservations_idempotency_key
    ON reservations(idempotency_key) WHERE idempotency_key IS NOT NULL;
